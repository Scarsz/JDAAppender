package me.scarsz.jdaappender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Function;

public class ExtensionBuilder {

    private static final Date DATE = new Date();
    private static final SimpleDateFormat DATE_FORMAT = utc(new SimpleDateFormat("MM/dd"));
    private static final SimpleDateFormat DATE_FORMAT_YEAR = utc(new SimpleDateFormat("MM/dd/yyyy"));
    private static final SimpleDateFormat TIME_FORMAT_12 = utc(new SimpleDateFormat("h:mm:ss aa"));
    private static final SimpleDateFormat TIME_FORMAT_24 = utc(new SimpleDateFormat("H:mm:ss"));

    private final List<Function<LogItem, String>> functions = new LinkedList<>();
    private final HandlerConfig config;

    public ExtensionBuilder(HandlerConfig config) {
        this.config = config;
    }

    public ExtensionBuilder space() {
        this.functions.add(item -> " ");
        return this;
    }
    public ExtensionBuilder text(String str) {
        this.functions.add(item -> str);
        return this;
    }
    public ExtensionBuilder level() {
        this.functions.add(item -> item.getLevel().name());
        return this;
    }
    public ExtensionBuilder logger() {
        this.functions.add(item -> config.resolveLoggerName(item.getLogger()));
        return this;
    }
    public ExtensionBuilder time12Hours() {
        return timestamp(TIME_FORMAT_12);
    }
    public ExtensionBuilder time24Hours() {
        return timestamp(TIME_FORMAT_24);
    }
    public ExtensionBuilder date() {
        return timestamp(DATE_FORMAT);
    }
    public ExtensionBuilder dateWithYear() {
        return timestamp(DATE_FORMAT_YEAR);
    }
    public ExtensionBuilder timestamp(SimpleDateFormat format) {
        this.functions.add(item -> {
            synchronized (DATE) {
                DATE.setTime(item.getTimestamp());
                return format.format(DATE);
            }
        });
        return this;
    }

    public Function<LogItem, String> build() {
        return logItem -> {
            StringBuilder builder = new StringBuilder();
            for (Function<LogItem, String> function : functions) {
                builder.append(function.apply(logItem));
            }
            return builder.toString();
        };
    }

    private static SimpleDateFormat utc(SimpleDateFormat format) {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }

}
