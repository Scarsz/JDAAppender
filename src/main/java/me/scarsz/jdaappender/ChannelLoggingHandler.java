package me.scarsz.jdaappender;

import lombok.Getter;
import me.scarsz.jdaappender.adapter.JavaLoggingAdapter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckReturnValue;
import java.io.Flushable;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ChannelLoggingHandler implements Flushable {

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
        if (scheduledFuture == null) {
            scheduledFuture = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::flush, period, period, unit);
        }
        return this;
    }

    /**
     * RegEx pattern used to check if a URL contains a link for use with {@link HandlerConfig#isAllowLinkEmbeds()}
     */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private final HandlerConfig config = new HandlerConfig();
    private final Deque<LogItem> messageQueue = new LinkedList<>();
    private final Set<LogItem> stack = new LinkedHashSet<>();
    private Message currentMessage = null;
    private Supplier<TextChannel> channelSupplier;

    public ChannelLoggingHandler(@NotNull Supplier<TextChannel> channelSupplier) {
        this(channelSupplier, null);
    }
    public ChannelLoggingHandler(@NotNull Supplier<TextChannel> channelSupplier, @Nullable Consumer<HandlerConfig> configConsumer) {
        this.channelSupplier = channelSupplier;
        if (configConsumer != null) configConsumer.accept(this.config);
    }

    public void enqueue(LogItem item) {
        LogItem overflow = item.clip(config);
        messageQueue.add(item);
        if (overflow != null) enqueue(overflow);
    }

    @Override
    public void flush() {
        TextChannel loggingChannel = channelSupplier.get();
        if (loggingChannel != null) {
            LogItem logItem;
            while ((logItem = messageQueue.poll()) != null) {
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

        if (config.isAllowLinkEmbeds()) {
            lengthSum += "```".length() * 2;
            lengthSum += "\n".length() * 2;
            if (config.isColored()) {
                lengthSum += "diff".length();
            }
        }

        return lengthSum + logItem.format(config).length() + 5 <= Message.MAX_CONTENT_LENGTH;
    }

    @CheckReturnValue
    public MessageAction updateMessage() {
        if (stack.size() == 0) throw new IllegalStateException("No messages on stack");

        StringJoiner joiner = new StringJoiner("\n");
        for (LogItem item : stack) {
            boolean willSplit = config.isAllowLinkEmbeds() && URL_PATTERN.matcher(item.getMessage()).find();

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
    public void replaceChannel(@Nullable String reason) {
        TextChannel channel = channelSupplier.get();
        channel.createCopy()
                .setPosition(channel.getPositionRaw())
                .flatMap(textChannel -> {
                    channelSupplier = () -> textChannel;
                    return channel.delete().reason(reason);
                })
                .complete();
    }

    public ChannelLoggingHandler attachJavaLogging() {
        java.util.logging.Logger.getLogger("").addHandler(new JavaLoggingAdapter(this));
        return this;
    }

}
