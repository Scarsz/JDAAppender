package me.scarsz.jdaappender;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
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

    private static final Function<String, String> friendlyMapper = s -> {
        String[] split = s.split("\\.");
        return split[split.length - 1];
    };

    /**
     * See {@link #loggerMappings}. Shortcut for loggerMappings.put(prefix, v -> friendlyName).
     * <strong>Logger mappings are implemented in the default logging prefix! Changing the prefixer will require reimplementation of logger mappings!</strong>
     *
     * <pre>
     * // translate "net.dv8tion.jda*" logger names to simply "JDA"
     * handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
     *
     * // translate loggers in a "modules" package of your app to their simple class name + " module"
     * handlerConfig.mapLoggerNameFriendly("your.application.package.modules", name -> name + " module");
     *
     * // translate loggers in your application to the simple class name of the logger
     * handlerConfig.mapLoggerNameFriendly("your.application.package");
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
     * // translate loggers in a "modules" package of your app to their simple class name + " module"
     * handlerConfig.mapLoggerNameFriendly("your.application.package.modules", name -> name + " module");
     *
     * // translate loggers in your application to the simple class name of the logger
     * handlerConfig.mapLoggerNameFriendly("your.application.package");
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
     * // translate loggers in a "modules" package of your app to their simple class name + " module"
     * handlerConfig.mapLoggerNameFriendly("your.application.package.modules", name -> name + " module");
     *
     * // translate loggers in your application to the simple class name of the logger
     * handlerConfig.mapLoggerNameFriendly("your.application.package");
     * </pre>
     * @param prefix the logger name to match
     */
    public void mapLoggerNameFriendly(String prefix) {
        loggerMappings.put(prefix, friendlyMapper);
    }
    /**
     * See {@link #loggerMappings}. Shortcut for loggerMappings.put(class prefix, class -> function(class simple name)).
     * <strong>Logger mappings are implemented in the default logging prefix! Changing the prefixer will require reimplementation of logger mappings!</strong>
     *
     * <pre>
     * // translate "net.dv8tion.jda*" logger names to simply "JDA"
     * handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
     *
     * // translate loggers in a "modules" package of your app to their simple class name + " module"
     * handlerConfig.mapLoggerNameFriendly("your.application.package.modules", name -> name + " module");
     *
     * // translate loggers in your application to the simple class name of the logger
     * handlerConfig.mapLoggerNameFriendly("your.application.package");
     * </pre>
     * @param prefix the logger name to match
     * @param function the function to modify the determined friendly name
     */
    public void mapLoggerNameFriendly(String prefix, Function<String, String> function) {
        loggerMappings.put(prefix, s -> function.apply(friendlyMapper.apply(s)));
    }

    /**
     * Function to include any relevant details as a prefix to a {@link LogItem}'s content when formatting.
     * Default equates to "[LEVEL Logger] ".
     */
    @Getter @Setter @Nullable private Function<LogItem, String> prefixer = item -> "[" + item.getLevel().name() + " " + resolveLoggerName(item.getLogger()) + "] ";

    /**
     * Resolve the given logger name with any configured logger name mappings
     * @param name the logger name to resolve mappings for
     * @return the resolved logger name if mapped, same as input otherwise
     */
    public String resolveLoggerName(@NotNull String name) {
        for (Map.Entry<String, Function<String, String>> entry : loggerMappings.entrySet()) {
            if (name.startsWith(entry.getKey())) {
                return entry.getValue().apply(name);
            }
        }
        return name;
    }

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
