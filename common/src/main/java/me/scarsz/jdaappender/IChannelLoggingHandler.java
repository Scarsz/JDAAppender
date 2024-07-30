package me.scarsz.jdaappender;

import java.io.InterruptedIOException;
import java.util.concurrent.ScheduledFuture;

public interface IChannelLoggingHandler {

    void enqueue(LogItem logItem);

    void flush();

    String escapeMarkdown(String message);

    ScheduledFuture<?> getScheduledFuture();

    default boolean isInterruptedException(Exception e) {
        Throwable ex = e;
        while (ex != null) {
            if (ex instanceof InterruptedIOException || ex instanceof InterruptedException) return true;
            ex = ex.getCause();
        }
        return false;
    }
}
