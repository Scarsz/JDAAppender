package me.scarsz.jdaappender;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Configuration for the associated {@link ChannelLoggingHandler}
 */
@SuppressWarnings("unused")
public class HandlerConfig {

    /**
     * Mappings representing a logger name prefix and associated Functions to transform those logger names.
     * Used to provide a more user-friendly name for a logger, such as translating "net.dv8tion.jda" to "JDA".
     * <strong>Logger mappings are implemented in the default logging prefix! Changing the prefixer will require reimplementation of logger mappings!</strong>
     */
    @Getter private final Map<String, Function<String, String>> loggerMappings = new HashMap<>();
    /**
     * See {@link #loggerMappings}. Shortcut for loggerMappings.put(prefix, v -> friendlyName).
     * <strong>Logger mappings are implemented in the default logging prefix! Changing the prefixer will require reimplementation of logger mappings!</strong>
     *
     * <pre>
     * // translate "net.dv8tion.jda*" logger names to simply "JDA"
     * handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
     *
     * // translate loggers in a "modules" package of your app to their simple class name
     * handlerConfig.mapLoggerNameFriendly("your.application.package.modules");
     * </pre>
     * @param prefix the logger name to match
     * @param friendlyName the friendly name to replace the logger name with
     */
    public void mapLoggerName(String prefix, String friendlyName) {
        loggerMappings.put(prefix, s -> friendlyName);
    }
    /**
     * See {@link #loggerMappings}. Shortcut for loggerMappings.put(prefix, function).
     * <strong>Logger mappings are implemented in the default logging prefix! Changing the prefixer will require reimplementation of logger mappings!</strong>
     *
     * <pre>
     * // translate "net.dv8tion.jda*" logger names to simply "JDA"
     * handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
     *
     * // translate loggers in a "modules" package of your app to their simple class name
     * handlerConfig.mapLoggerNameFriendly("your.application.package.modules");
     * </pre>
     * @param prefix the logger name to match
     * @param function the mapping function
     */
    public void mapLoggerName(String prefix, Function<String, String> function) {
        loggerMappings.put(prefix, function);
    }
    /**
     * See {@link #loggerMappings}. Shortcut for loggerMappings.put(class prefix, class -> class simple name).
     * <strong>Logger mappings are implemented in the default logging prefix! Changing the prefixer will require reimplementation of logger mappings!</strong>
     *
     * <pre>
     * // translate "net.dv8tion.jda*" logger names to simply "JDA"
     * handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
     *
     * // translate loggers in a "modules" package of your app to their simple class name
     * handlerConfig.mapLoggerNameFriendly("your.application.package.modules");
     * </pre>
     * @param prefix the logger name to match
     */
    public void mapLoggerNameFriendly(String prefix) {
        loggerMappings.put(prefix, s -> {
            String[] split = s.split("\\.");
            return split[split.length - 1];
        });
    }

    /**
     * Function to include any relevant details as a prefix to a {@link LogItem}'s content when formatting.
     * Default equates to "[LEVEL Logger] ".
     */
    @Getter @Setter @Nullable private Function<LogItem, String> prefixer = item -> {
        String loggerName = item.getLogger();
        for (Map.Entry<String, Function<String, String>> entry : loggerMappings.entrySet()) {
            if (loggerName.startsWith(entry.getKey())) {
                loggerName = entry.getValue().apply(loggerName);
                break;
            }
        }
        return "[" + item.getLevel().name() + " " + loggerName + "] ";
    };

    /**
     * Function to include any relevant details as a suffix to a {@link LogItem}'s content when formatting.
     * Default null.
     */
    @Getter @Setter @Nullable private Function<LogItem, String> suffixer;

    /**
     * Whether the logging handler should format log items which contain a URL to be outside the output code blocks.
     * This is useful for if you want links to be clickable or not in the Discord client.
     * Has the tradeoff that the log item will have no coloring/monospace font.
     * Default false.
     */
    @Getter @Setter private boolean splitCodeBlockForLinks = false;

    /**
     * Whether the logging handler should allow Discord to show embeds for links when {@link #splitCodeBlockForLinks} is enabled.
     * Default true.
     */
    @Getter @Setter private boolean allowLinkEmbeds = true;

    /**
     * Whether the logging handler should format log items with code syntax to highlight log levels in distinct colors.
     * Default true.
     */
    @Getter @Setter private boolean colored = true;

}
