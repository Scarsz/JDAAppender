package me.scarsz.jdaappender;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import me.scarsz.jdaappender.adapter.JavaLoggingAdapter;
import me.scarsz.jdaappender.adapter.SystemLoggingAdapter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Flushable;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ChannelLoggingHandler implements IChannelLoggingHandler, Flushable {

    @Getter private ScheduledExecutorService executor;
    @Getter private ScheduledFuture<?> scheduledFuture;

    /**
     * Schedule the handler to asynchronously flush to the logging channel every second.
     * @return this channel logging handler
     */
    public ChannelLoggingHandler schedule() {
        return schedule(1500, TimeUnit.MILLISECONDS);
    }
    /**
     * Schedule the handler to asynchronously flush to the logging channel every {period} {unit}.
     * Default is every second.
     * @param period amount of the given unit between flushes
     * @param unit the unit that the given amount is expressed in
     * @return this channel logging handler
     */
    public ChannelLoggingHandler schedule(long period, @NotNull TimeUnit unit) {
        shutdownExecutor(); // Stop the existing executor, if one exists
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        if (scheduledFuture == null) {
            scheduledFuture = executor.scheduleAtFixedRate(() -> {
                try {
                    this.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, period, period, unit);
        }
        return this;
    }

    /**
     * RegEx pattern used to check if a URL contains a link for use with {@link HandlerConfig#isAllowLinkEmbeds()}
     */
    private static final Pattern URL_PATTERN = Pattern.compile("https?:\\/\\/((?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9]:?\\d*\\/?[a-zA-Z0-9_\\/\\-#.]*\\??[a-zA-Z0-9\\-_~:\\/?#\\[\\]@!$&'()*+,;=%.]*)");

    /**
     * Error code for "Message blocked by harmful links filter" ErrorResponse
     */
    private static final int MESSAGE_BLOCKED_BY_HARMFUL_LINK_FILTER_ERROR_CODE = 240000;

    @Getter private final HandlerConfig config = new HandlerConfig();
    @Getter private final Deque<LogItem> messageQueue = new LinkedList<>();
    private final Deque<LogItem> unprocessedQueue = new ConcurrentLinkedDeque<>();
    @Getter private final Set<LogItem> stack = new LinkedHashSet<>();
    @Getter private final AtomicBoolean dirtyBit = new AtomicBoolean();
    @Getter private Supplier<MessageChannel> channelSupplier;
    private final Set<Runnable> detachRunnables = new HashSet<>();
    private Message currentMessage = null;

    public ChannelLoggingHandler(@NotNull Supplier<MessageChannel> channelSupplier) {
        this(channelSupplier, null);
    }
    public ChannelLoggingHandler(@NotNull Supplier<MessageChannel> channelSupplier, @Nullable Consumer<HandlerConfig> configConsumer) {
        this.channelSupplier = channelSupplier;
        if (configConsumer != null) configConsumer.accept(this.config);
    }

    public void enqueue(LogItem item) {
        unprocessedQueue.add(item);
    }

    private void process(LogItem item) {
        if (!config.getLogLevels().contains(item.getLevel())) return;
        if (config.resolveLoggerName(item.getLogger()) == null) return;

        // check for any filtering transformers
        for (Map.Entry<Predicate<LogItem>, Function<String, String>> entry : config.getMessageTransformers().entrySet()) {
            if (entry.getKey().test(item) && entry.getValue().apply(item.getMessage()) == null) {
                return;
            }
        }

        // allow transformers to modify log message if no filters denied it
        for (Map.Entry<Predicate<LogItem>, Function<String, String>> entry : config.getMessageTransformers().entrySet()) {
            if (entry.getKey().test(item)) {
                item.setMessage(entry.getValue().apply(item.getMessage()));
            }
        }

        Set<LogItem> clipped = item.clip(config, (int) (Math.ceil((double) (10_000 - Message.MAX_CONTENT_LENGTH) / Message.MAX_CONTENT_LENGTH)));
        messageQueue.add(item);
        messageQueue.addAll(clipped);
    }

    @Override
    public void flush() {
        LogItem currentItem;
        while ((currentItem = unprocessedQueue.poll()) != null) {
            process(currentItem);
        }

        MessageChannel loggingChannel = channelSupplier.get();
        if (loggingChannel != null && loggingChannel.getJDA().getStatus() == JDA.Status.CONNECTED) {
            LogItem logItem;
            synchronized (stack) {
                while ((logItem = messageQueue.poll()) != null) {
                    if (logItem.getMessage() == null && logItem.getThrowable() == null) {
                        // Nothing to log, likely due to being cleared during formatting
                        continue;
                    }

                    if (logItem.getFormattedLength(config) > LogItem.CLIPPING_MAX_LENGTH) {
                        throw new IllegalStateException("Log item longer than Discord's max content length: " + logItem);
                    }

                    if (!canFit(logItem)) {
                        if (stack.isEmpty()) throw new IllegalStateException("Can't fit LogItem into empty stack: " + logItem);
                        dumpStack();
                    }

                    stack.add(logItem);
                    dirtyBit.set(true);
                }

                if (dirtyBit.get() && !stack.isEmpty()) {
                    currentMessage = updateMessage();
                    dirtyBit.set(false);
                }
            }
        }
    }

    /**
     * Push the current LogItem stack to Discord, then dump the stack, starting a new message.
     */
    @Synchronized("stack")
    public void dumpStack() {
        try {
            if (!stack.isEmpty()) updateMessage();
        } catch (IllegalStateException ignored) {}
        stack.clear();
        currentMessage = null;
    }

    /**
     * Whether the given {@link LogItem} is able to fit in the current {@link #stack}. Internal usage.
     * @param logItem the log item to check for fitment of
     * @return true if the log item will fit, false if it won't and a new stack + message will be started to accommodate
     */
    @Synchronized("stack")
    public boolean canFit(LogItem logItem) {
        int lengthSum = 0;
        for (LogItem item : stack) {
            String formatted = item.format(config);
            int length = formatted.length();
            lengthSum += length;
        }

        boolean codeBlocks = config.isUseCodeBlocks();
        if (codeBlocks) lengthSum += "```".length() * 2; // code block backticks
        lengthSum += "\n".length() * (stack.size() + (codeBlocks ? 1 : -1)); // newlines (one per element + 1 (with code blocks) or - 1 (without code blocks))

        if (config.isColored()) {
            lengthSum += "diff".length(); // language
            lengthSum += "- ".length() * stack.size(); // language symbols
        }

        if (config.isSplitCodeBlockForLinks()) {
            lengthSum += "```".length() * 2;
            lengthSum += "\n".length() * 2;
            if (config.isColored()) {
                lengthSum += "diff".length();
            }
        }

        return lengthSum + logItem.format(config).length() + 5 <= Message.MAX_CONTENT_LENGTH;
    }

    private Message updateMessage() throws IllegalStateException {
        MessageChannel channel;
        StringJoiner joiner;

        synchronized (stack) {
            if (stack.isEmpty()) throw new IllegalStateException("No messages on stack");

            channel = channelSupplier.get();
            if (channel == null) throw new IllegalStateException("Channel unavailable");

            joiner = new StringJoiner("\n");
            for (LogItem item : stack) {
                boolean willSplit = config.isSplitCodeBlockForLinks() && item.getMessage() != null && URL_PATTERN.matcher(item.getMessage()).find();

                String formatted = item.format(config);

                if (!willSplit && config.isColored()) {
                    formatted = item.getLevel().getLevelSymbol() + " " + formatted;
                }

                if (willSplit) {
                    joiner.add("```\n" + formatted + "\n```" + (config.isColored() ? "diff" : ""));
                } else {
                    joiner.add(formatted);
                }
            }
        }

        boolean codeBlock = config.isUseCodeBlocks();
        String full = codeBlock ? "```" + (config.isColored() ? "diff" : "") + "\n" + joiner + "```" : joiner.toString();

        if (codeBlock) {
            // safeguard against empty codeblocks
            full = full.replace("```" + (config.isColored() ? "diff" : "") + "```", "");
            full = full.replace("```" + (config.isColored() ? "diff" : "") + "\n```", "");
        }

        // safeguard against empty lines
        while (full.contains("\n\n")) full = full.replace("\n\n", "\n");

        try {
            return sendOrEditMessage(full, channel);
        } catch (ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                currentMessage = null;
                return sendOrEditMessage(full, channel);
            } else if (errorResponseException.getErrorCode() == MESSAGE_BLOCKED_BY_HARMFUL_LINK_FILTER_ERROR_CODE) {
                full = URL_PATTERN.matcher(full).replaceAll("$1");
                return sendOrEditMessage(full, channel);
            } else {
                throw new RuntimeException(errorResponseException);
            }
        }
    }

    private Message sendOrEditMessage(String full, MessageChannel channel) throws ErrorResponseException {
        try {
            if (currentMessage != null) {
                return currentMessage.editMessage(full).submit().get();
            } else {
                return channel.sendMessage(full).submit().get();
            }
        } catch (ExecutionException | InterruptedException e) {
            if (this.isInterruptedException(e)) return currentMessage;

            if (e.getCause() instanceof ErrorResponseException) {
                throw (ErrorResponseException) e.getCause();
            }

            throw new RuntimeException(e);
        }
    }

    /**
     * Recreate and replace the channel that this logging handler is assigned to.
     * Useful when cold-starting the application on initialization to quickly purge all previous messages.
     * Be aware that the original channel will be <strong>deleted</strong>!
     * The new channel will <strong>not</strong> have the same channel ID.
     */
    public void recreateChannel(@Nullable String reason) {
        MessageChannel uncheckedChannel = channelSupplier.get();
        if (!(uncheckedChannel instanceof TextChannel)) {
            throw new IllegalStateException("recreateChannel is only implemented for instances of TextChannel");
        }

        TextChannel channel = (TextChannel) uncheckedChannel;
        channel.createCopy()
                .setPosition(channel.getPositionRaw())
                .flatMap(textChannel -> {
                    channelSupplier = () -> textChannel;
                    return channel.delete().reason(reason);
                })
                .complete();
    }

    /**
     * Shutdown the internal executor, if active.
     * @see #schedule()
     * @see #schedule(long, TimeUnit)
     */
    public void shutdownExecutor() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // ignore, it's fine if the last tidbit of console output doesn't get sent
            }
            executor = null;
        }
    }

    /**
     * Shuts down the internal executor, and detaches attached loggers.
     * @see #shutdownExecutor()
     * @see #detach()
     */
    public void shutdown() {
        detach();
        shutdownExecutor();
    }

    public ChannelLoggingHandler attach() {
        // log4j?
        try {
            Class.forName("org.apache.logging.log4j.core.Logger");
            return attachLog4jLogging();
        } catch (Throwable ignored) {}

        // logback?
        try {
            Class.forName("ch.qos.logback.core.Appender");
            return attachLogbackLogging();
        } catch (Throwable ignored) {}

        // slf4j?
        try {
            Class<?> logFactoryClass = Class.forName(org.slf4j.impl.StaticLoggerBinder.getSingleton().getLoggerFactoryClassStr());
            switch (logFactoryClass.getSimpleName()) {
                case "JDK14LoggerFactory": return attachJavaLogging();
                case "ContextSelectorStaticBinder": return attachLogbackLogging();
                //TODO more SLF4J implementations
                default:
                    System.err.println("SLF4J Logger factory " + logFactoryClass.getName() + " is not supported");
                    enqueue(new LogItem(this, "Appender", LogLevel.ERROR, "SLF4J Logger factory " + logFactoryClass.getName() + " is not supported"));
            }
        } catch (Throwable ignored) {}

        return attachSystemLogging();
    }
    public void detach() {
        Iterator<Runnable> iterator = detachRunnables.iterator();
        while (iterator.hasNext()) {
            Runnable runnable = iterator.next();
            runnable.run();
            iterator.remove();
        }
    }
    public ChannelLoggingHandler attachSystemLogging() {
        SystemLoggingAdapter adapter = new SystemLoggingAdapter(this);
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(adapter.getOutStream());
        System.setErr(adapter.getErrStream());
        detachRunnables.add(() -> {
            System.setOut(originalOut);
            System.setErr(originalErr);
        });
        return this;
    }
    public ChannelLoggingHandler attachJavaLogging() {
        JavaLoggingAdapter adapter = new JavaLoggingAdapter(this);
        java.util.logging.Logger.getLogger("").addHandler(adapter);
        detachRunnables.add(() -> java.util.logging.Logger.getLogger("").removeHandler(adapter));
        return this;
    }
    @SneakyThrows
    public ChannelLoggingHandler attachLog4jLogging() {
        org.apache.logging.log4j.Logger rootLogger = org.apache.logging.log4j.LogManager.getRootLogger();
        Method addAppenderMethod = rootLogger.getClass().getMethod("addAppender", org.apache.logging.log4j.core.Appender.class);
        Method removeAppenderMethod = rootLogger.getClass().getMethod("removeAppender", org.apache.logging.log4j.core.Appender.class);

        Object adapter = Class.forName("me.scarsz.jdaappender.adapter.Log4JLoggingAdapter")
                .getConstructor(IChannelLoggingHandler.class)
                .newInstance(this);
        addAppenderMethod.invoke(rootLogger, adapter);

        detachRunnables.add(() -> {
            try {
                removeAppenderMethod.invoke(rootLogger, adapter);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return this;
    }
    @SneakyThrows
    public ChannelLoggingHandler attachLogbackLogging() {
        ch.qos.logback.classic.LoggerContext loggerContext = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        org.slf4j.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        Method addAppenderMethod = rootLogger.getClass().getMethod("addAppender", ch.qos.logback.core.Appender.class);
        Method detachAppenderMethod = rootLogger.getClass().getMethod("detachAppender", ch.qos.logback.core.Appender.class);

        Object adapter = Class.forName("me.scarsz.jdaappender.adapter.LogbackLoggingAdapter")
                .getConstructor(ChannelLoggingHandler.class, ch.qos.logback.classic.LoggerContext.class)
                .newInstance(this, loggerContext);
        addAppenderMethod.invoke(rootLogger, adapter);

        detachRunnables.add(() -> {
            try {
                detachAppenderMethod.invoke(rootLogger, adapter);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return this;
    }

    @Override
    public String escapeMarkdown(String message) {
        return MarkdownSanitizer.escape(message);
    }

}
