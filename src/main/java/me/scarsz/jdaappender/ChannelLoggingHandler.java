package me.scarsz.jdaappender;

import lombok.Getter;
import lombok.SneakyThrows;
import me.scarsz.jdaappender.adapter.StandardLoggingAdapter;
import me.scarsz.jdaappender.adapter.slf4j.JavaLoggingAdapter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckReturnValue;
import java.io.Flushable;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ChannelLoggingHandler implements Flushable {

    @Getter private ScheduledExecutorService executor;
    @Getter private ScheduledFuture<?> scheduledFuture;

    /**
     * Schedule the handler to asynchronously flush to the logging channel every second.
     * @return this channel logging handler
     */
    public ChannelLoggingHandler schedule() {
        return schedule(1, TimeUnit.SECONDS);
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
            scheduledFuture = executor.scheduleAtFixedRate(this::flush, period, period, unit);
        }
        return this;
    }

    /**
     * RegEx pattern used to check if a URL contains a link for use with {@link HandlerConfig#isAllowLinkEmbeds()}
     */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    @Getter private final HandlerConfig config = new HandlerConfig();
    @Getter private final Deque<LogItem> messageQueue = new LinkedList<>();
    @Getter private final Set<LogItem> stack = new LinkedHashSet<>();
    @Getter private Supplier<TextChannel> channelSupplier;
    private final Set<Runnable> detachRunnables = new HashSet<>();
    private Message currentMessage = null;

    public ChannelLoggingHandler(@NotNull Supplier<TextChannel> channelSupplier) {
        this(channelSupplier, null);
    }
    public ChannelLoggingHandler(@NotNull Supplier<TextChannel> channelSupplier, @Nullable Consumer<HandlerConfig> configConsumer) {
        this.channelSupplier = channelSupplier;
        if (configConsumer != null) configConsumer.accept(this.config);
    }

    public void enqueue(LogItem item) {
        Set<LogItem> clipped = item.clip(config, 4);
        messageQueue.add(item);
        messageQueue.addAll(clipped);
    }

    @Override
    public void flush() {
        TextChannel loggingChannel = channelSupplier.get();
        if (loggingChannel != null) {
            LogItem logItem;
            while ((logItem = messageQueue.poll()) != null) {
                if (logItem.getFormattedLength(config) >= Message.MAX_CONTENT_LENGTH - 15) {
                    throw new IllegalStateException("Log item longer than Discord's max content length");
                }

                if (!canFit(logItem)) {
                    updateMessage().complete();
                    currentMessage = null;
                    stack.clear();
                }

                stack.add(logItem);
            }

            currentMessage = updateMessage().complete();
        }
    }

    /**
     * Whether the given {@link LogItem} is able to fit in the current {@link #stack}. Internal usage.
     * @param logItem the log item to check for fitment of
     * @return true if the log item will fit, false if it won't and a new stack + message will be started to accommodate
     */
    public boolean canFit(LogItem logItem) {
        int lengthSum = 0;
        for (LogItem item : stack) {
            String formatted = item.format(config);
            int length = formatted.length();
            lengthSum += length;
        }
        lengthSum += "```".length() * 2; // code block backticks
        lengthSum += "\n".length() * (stack.size() + 1); // newlines (one per element + 1)

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

    @CheckReturnValue
    private MessageAction updateMessage() {
        if (stack.size() == 0) throw new IllegalStateException("No messages on stack");

        StringJoiner joiner = new StringJoiner("\n");
        for (LogItem item : stack) {
            boolean willSplit = config.isSplitCodeBlockForLinks() && URL_PATTERN.matcher(item.getMessage()).find();

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
        String full = "```" + (config.isColored() ? "diff" : "") + "\n" + joiner + "```";

        // safeguard against empty codeblocks
        full = full.replace("```" + (config.isColored() ? "diff" : "") + "```", "");
        full = full.replace("```" + (config.isColored() ? "diff" : "") + "\n```", "");

        // safeguard against empty lines
        while (full.contains("\n\n")) full = full.replace("\n\n", "\n");

        MessageAction action;
        if (currentMessage == null) {
            action = channelSupplier.get().sendMessage(full);
        } else {
            action = currentMessage.editMessage(full);
        }
        return action;
    }

    /**
     * Recreate and replace the channel that this logging handler is assigned to.
     * Useful when cold-starting the application on initialization to quickly purge all previous messages.
     * Be aware that the original channel will be <strong>deleted</strong>!
     * The new channel will <strong>not</strong> have the same channel ID.
     */
    public void recreateChannel(@Nullable String reason) {
        TextChannel channel = channelSupplier.get();
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
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
        if (executor != null) {
            executor.shutdown();
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

        // slf4j?
        try {
            Class<?> logFactoryClass = Class.forName(org.slf4j.impl.StaticLoggerBinder.getSingleton().getLoggerFactoryClassStr());
            switch (logFactoryClass.getSimpleName()) {
                case "JDK14LoggerFactory": return attachJavaLogging();
                //TODO more SLF4J implementations
            }
        } catch (Throwable ignored) {}

        return attachStandardLogging();
    }
    public void detach() {
        Iterator<Runnable> iterator = detachRunnables.iterator();
        while (iterator.hasNext()) {
            Runnable runnable = iterator.next();
            runnable.run();
            iterator.remove();
        }
    }
    public ChannelLoggingHandler attachStandardLogging() {
        StandardLoggingAdapter adapter = new StandardLoggingAdapter(this);
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
        Object rootLogger = Class.forName("org.apache.logging.log4j.LogManager").getMethod("getRootLogger").invoke(null);
        Method addAppenderMethod = Arrays.stream(rootLogger.getClass().getMethods())
                .filter(method -> method.getName().equals("addAppender"))
                .findFirst().orElseThrow(() -> new RuntimeException("No RootLogger#addAppender method"));
        Method removeAppenderMethod = Arrays.stream(rootLogger.getClass().getMethods())
                .filter(method -> method.getName().equals("removeAppender"))
                .findFirst().orElseThrow(() -> new RuntimeException("No RootLogger#removeAppender method"));

        Object adapter = Class.forName("me.scarsz.jdaappender.adapter.Log4JLoggingAdapter").getConstructor(ChannelLoggingHandler.class).newInstance(this);
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

}
