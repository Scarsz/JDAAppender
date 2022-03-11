package me.scarsz.jdaappender.adapter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import me.scarsz.jdaappender.ChannelLoggingHandler;
import me.scarsz.jdaappender.LogItem;
import me.scarsz.jdaappender.LogLevel;

public class LogbackLoggingAdapter extends AppenderBase<ILoggingEvent> {

    private final ChannelLoggingHandler handler;

    public LogbackLoggingAdapter(ChannelLoggingHandler handler, LoggerContext context) {
        this.handler = handler;
        setContext(context);
        this.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        LogLevel level = event.getLevel() == Level.INFO ? LogLevel.INFO
                : event.getLevel() == Level.WARN ? LogLevel.WARN
                : event.getLevel() == Level.ERROR ? LogLevel.ERROR
                : event.getLevel() == Level.DEBUG ? LogLevel.DEBUG
                : null;

        if (level != null) {
            handler.enqueue(new LogItem(
                    event.getLoggerName(),
                    event.getTimeStamp(),
                    level,
                    LogItem.stripColors(event.getMessage()),
                    event.getThrowableProxy() instanceof ThrowableProxy ? ((ThrowableProxy) event.getThrowableProxy()).getThrowable() : null // serialized throwable data?
            ));
        }
    }

}
