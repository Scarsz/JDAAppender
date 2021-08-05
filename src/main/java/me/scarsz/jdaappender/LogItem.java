package me.scarsz.jdaappender;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import org.jetbrains.annotations.NotNull;

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
        return (config.getPrefixer() != null ? config.getPrefixer().apply(this) : "")
                + message
                + (config.getSuffixer() != null ? config.getSuffixer().apply(this) : "");
    }

    /**
     * Clip the log item's message content if it exceeds {@link Message#MAX_CONTENT_LENGTH}
     * @param config the appender config
     * @return a new {@link LogItem} containing excess characters from this LogItem,
     *         null if no clipping was performed
     */
    protected LogItem clip(@NotNull HandlerConfig config) {
        int fullLength = format(config).length();
        if (fullLength >= Message.MAX_CONTENT_LENGTH - 15) {
            String original = message;
            message = substring(message, 0, fullLength);
            return new LogItem(logger, timestamp, level, substring(original, fullLength), throwable);
        } else {
            return null;
        }
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
