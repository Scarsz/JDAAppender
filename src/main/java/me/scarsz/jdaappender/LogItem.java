package me.scarsz.jdaappender;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Represents a loggable message from the application
 */
public class LogItem {

    @Getter private final String logger;
    @Getter private final long timestamp;
    @Getter private final LogLevel level;
    @Getter private String message;
    @Getter private final Throwable throwable;

    public LogItem(String logger, long timestamp, LogLevel level, String message, Throwable throwable) {
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
        builder.append(message);
        if (config.getSuffixer() != null) builder.append(config.getSuffixer().apply(this));
        if (throwable != null) {
            for (StackTraceElement element : throwable.getStackTrace()) {
                builder.append(element.toString());
            }
        }

        return builder.toString();
    }

    /**
     * Clip the log item's message content if it exceeds {@link Message#MAX_CONTENT_LENGTH}
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
     * {@link Message#MAX_CONTENT_LENGTH}
     * @param config the appender config
     * @param count the maximum amount of {@link LogItem}s to clip from this message
     * @return a new {@link LogItem} containing excess characters from this LogItem,
     *         null if no clipping was performed
     */
    protected Set<LogItem> clip(@NotNull HandlerConfig config, int count) {
        Set<LogItem> items = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            int fullLength = format(config).length();
            if (fullLength >= Message.MAX_CONTENT_LENGTH - 15) {
                String original = message;
                message = substring(message, 0, fullLength);
                items.add(new LogItem(logger, timestamp, level, substring(original, fullLength), throwable));
            } else {
                break;
            }
        }
        return items;
    }

    /**
     * regex-powered aggressive stripping pattern, see https://regex101.com/r/RDcGRE for explanation
     */
    private static final Pattern colorPattern = Pattern.compile("\u001B(?:\\[0?m|\\[38;2(?:;\\d{1,3}){3}m|\\[([0-9]{1,2}[;m]?){3})");
    public static String stripColors(@NotNull String str) {
        return colorPattern.matcher(str).replaceAll("");
    }

    private String substring(String str, int start) {
        return substring(str, start, str.length());
    }
    private static String substring(String str, int start, int end) {
        if (end < 0) end += str.length();
        if (start < 0) start = 0;
        if (end > str.length()) end = str.length();
        return start > end ? "" : str.substring(start, end);
    }

}
