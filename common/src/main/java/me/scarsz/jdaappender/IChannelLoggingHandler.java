package me.scarsz.jdaappender;

import java.util.concurrent.ScheduledFuture;

public interface IChannelLoggingHandler {

    void enqueue(LogItem logItem);

    void flush();

    String escapeMarkdown(String message);

    ScheduledFuture<?> getScheduledFuture();

}
