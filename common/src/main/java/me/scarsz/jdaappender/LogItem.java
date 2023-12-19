package me.scarsz.jdaappender;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Represents a loggable message from the application
 */
@Getter
public class LogItem {

    public static final int CLIPPING_MAX_LENGTH = 2000 - 20;

    private final IChannelLoggingHandler handler;
    private final String logger;
    private final long timestamp;
    private final LogLevel level;
    @Setter(AccessLevel.PACKAGE) @Nullable private String message;
    @Nullable private final Throwable throwable;

    public LogItem(IChannelLoggingHandler handler, String logger, LogLevel level, String message) {
        this(handler, logger, System.currentTimeMillis(), level, message, null);
    }
    public LogItem(IChannelLoggingHandler handler, String logger, long timestamp, LogLevel level, @Nullable String message, @Nullable Throwable throwable) {
        this.handler = handler;
        this.logger = logger;
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.throwable = throwable;
    }

    /**
     * Format the log item's content to a usable {@link String}
     * @param config the appender config
     * @return the human-readable, formatted line representing this LogItem
     */
    protected String format(@NotNull HandlerConfig config) {
        StringBuilder builder = new StringBuilder();

        if (config.getPrefixer() != null) builder.append(config.getPrefixer().apply(this));
        if (message != null) builder.append(config.isUseCodeBlocks() ? message.replace("```", "`\u200B`\u200B`\u200B") : handler.escapeMarkdown(message));
        if (config.getSuffixer() != null) builder.append(config.getSuffixer().apply(this));
        if (throwable != null) {
            try (StringWriter stringWriter = new StringWriter()) {
                try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
                    throwable.printStackTrace(printWriter);

                    builder.append('\n');
                    builder.append(stringWriter);
                }
            } catch (IOException ignored) {} // not possible
        }

        String s = builder.toString();
        return s.length() > CLIPPING_MAX_LENGTH ? s.substring(0, CLIPPING_MAX_LENGTH) : s;
    }

    /**
     * Clip the log item's message content if it exceeds {@link LogItem#CLIPPING_MAX_LENGTH}
     * @param config the appender config
     * @return a new {@link LogItem} containing excess characters from this LogItem,
     *         null if no clipping was performed
     */
    protected LogItem clip(@NotNull HandlerConfig config) {
        Iterator<LogItem> clip = clip(config, 1).iterator();
        return clip.hasNext() ? clip.next() : null;
    }
    /**
     * Clip the log item's message content into a maximum of specified number of log items, if it exceeds
     * {@link LogItem#CLIPPING_MAX_LENGTH}
     * @param config the appender config
     * @param max the maximum amount of {@link LogItem}s to clip from this message
     * @return a set containing {@link LogItem}s formed from excess characters in this LogItem,
     *         empty set if no clipping was performed
     */
    protected Set<LogItem> clip(@NotNull HandlerConfig config, int max) {
        Set<LogItem> items = new LinkedHashSet<>();

        LogItem bottom = this;
        int formattingLength = config.getFormattingLength(bottom);
        int i = 0;
        while (message != null && !message.isEmpty() && i < max && message.length() + formattingLength >= CLIPPING_MAX_LENGTH) {
            formattingLength = config.getFormattingLength(bottom);
            int cutoff = CLIPPING_MAX_LENGTH - formattingLength;
            int pulledCharacterCount = Math.min(cutoff, bottom.message.length());

            String remaining = substring(bottom.message, pulledCharacterCount);
            bottom.message = substring(bottom.message, 0, pulledCharacterCount);

            if (remaining == null || remaining.isEmpty()) break;
            if (++i == max) break;

            bottom = clone(remaining);
            if (bottom.message == null) return items;
            items.add(bottom);
        }

        return items;
    }

    /**
     * strip ANSI escape codes
     */
    private static final Pattern colorPattern = Pattern.compile("\u001B\\[[\\d;]*m");
    public static String stripColors(@NotNull String str) {
        return colorPattern.matcher(str).replaceAll("");
    }

    private @Nullable String substring(String str, int start) {
        return substring(str, start, str.length());
    }
    private static @Nullable String substring(String str, int start, int end) {
        if (str == null) return null;
        if (end < 0) end += str.length();
        if (start < 0) start = 0;
        if (end > str.length()) end = str.length();
        return start > end ? "" : str.substring(start, end);
    }

    public int getFormattedLength(HandlerConfig config) {
        return format(config).length();
    }

    public LogItem clone(String message) {
        return new LogItem(handler, logger, timestamp, level, message, throwable);
    }

    @Override
    public String toString() {
        return "LogItem{" +
                "logger='" + logger + '\'' +
                ", level=" + level +
                (message != null
                        ? ", message[" + message.length() + "]='" + (message.length() <= 100 ? message : message.substring(0, 100)) + '\''
                        : ", message[]=\"null"
                ) +
                '}';
    }

}
