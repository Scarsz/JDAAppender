package me.scarsz.jdaappender.adapter.slf4j;

import me.scarsz.jdaappender.ChannelLoggingHandler;
import me.scarsz.jdaappender.LogItem;
import me.scarsz.jdaappender.LogLevel;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class JavaLoggingAdapter extends Handler {

    private final ChannelLoggingHandler handler;

    public JavaLoggingAdapter(ChannelLoggingHandler handler) {
        this.handler = handler;
    }

    @Override
    public void publish(LogRecord record) {
        LogLevel level = record.getLevel() == Level.INFO
                ? LogLevel.INFO
                : record.getLevel() == Level.WARNING
                        ? LogLevel.WARN
                        : record.getLevel() == Level.SEVERE
                                ? LogLevel.ERROR
                                : null;

        if (level != null) {
            handler.enqueue(new LogItem(
                    record.getLoggerName(),
                    record.getMillis(),
                    level,
                    LogItem.stripColors(record.getMessage()),
                    record.getThrown()
            ));
        }
    }

    @Override
    public void flush() {
        handler.flush();
    }

    @Override
    public void close() throws SecurityException {
        ScheduledFuture<?> f = handler.getScheduledFuture();
        if (f != null) {
            f.cancel(false);
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
