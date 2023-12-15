package me.scarsz.jdaappender;

import java.util.concurrent.ScheduledFuture;

public interface IChannelLoggingHandler {

    void flush();

    void enqueue(LogItem logItem);

    ScheduledFuture<?> getScheduledFuture();

}
