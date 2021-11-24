package me.scarsz.jdaappender;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Configuration for the associated {@link ChannelLoggingHandler}
 */
@SuppressWarnings("unused")
public class HandlerConfig {

    /**
     * Message transformers that will be used to test incoming {@link LogItem}s before they are put in the queue.
     * Predicates should return {@code false} if it is neutral in respect to the LogItem; return {@code true} when the item should be modified/denied.
     * Can be used to block certain LogItems from being forwarded if, for example, it contains an unwanted message.
     */
    @Getter private final Map<Predicate<LogItem>, Function<String, String>> messageTransformers = new LinkedHashMap<>();

    /**
     * Adds a message transformer that will deny messages when the specified {@link Predicate} is {@code true}.
     * @param filter the predicate to filter {@link LogItem}s by
     */
    public void addFilter(Predicate<LogItem> filter) {
        messageTransformers.put(filter, s -> null);
    }

    /**
     * Adds a message transformer that will modify messages when the specified {@link Predicate} is {@code true}.
     * @param filter the predicate to filter {@link LogItem}s by
     */
    public void addTransformer(Predicate<LogItem> filter, Function<String, String> transformer) {
        messageTransformers.put(filter, transformer);
    }

    /**
     * Mappings representing a logger name prefix and associated Functions to transform those logger names.
     * Used to provide a more user-friendly name for a logger, such as translating "net.dv8tion.jda" to "JDA".
     * A logger name mapping may return {@code null} if messages from the logger should be ignored.
     * <strong>Logger mappings are implemented in the default logging prefix! Changing the prefixer will require reimplementation of logger mappings!</strong>
     */
    @Getter private final Map<String, Function<String, String>> loggerMappings = new LinkedHashMap<>();

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
     * See {@link #loggerMappings}. Ignores messages from the specified logger prefix. Shortcut for loggerMappings.put(prefix, v -> null).
     * <strong>Logger mappings are implemented in the default logging prefix! Changing the prefixer will require reimplementation of logger mappings!</strong>
     * @param prefix the logger name prefix to ignore
     */
    public void ignoreLoggerName(String prefix) {
        loggerMappings.put(prefix, s -> null);
    }

    /**
     * Function to include any relevant details as a prefix to a {@link LogItem}'s content when formatting.
     * Default equates to "[LEVEL Logger] ".
     */
    @Getter @Setter @Nullable private Function<LogItem, String> prefixer = item -> {
        String name = resolveLoggerName(item.getLogger());
        return "[" + item.getLevel().name() + (name != null && !name.isEmpty() ? " " + name : "") + "] ";
    };

    /**
     * Resolve the given logger name with any configured logger name mappings
     * @param name the logger name to resolve mappings for
     * @return if the logger name has been mapped to blank/null: null.
     * Otherwise, the resolved logger name if mapped, else same as input
     */
    public @Nullable String resolveLoggerName(@NotNull String name) {
        String resolved = name;

        for (Map.Entry<String, Function<String, String>> entry : loggerMappings.entrySet()) {
            if (name.startsWith(entry.getKey())) {
                resolved = entry.getValue().apply(name);
                break;
            }
        }

        return resolved;
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

    /**
     * Whether the logging handler should truncate {@link LogItem}s with a formatted length longer than {@link LogItem#CLIPPING_MAX_LENGTH}.
     * Default true.
     */
    @Getter @Setter private boolean truncateLongItems = true;


    /**
     * Check how many characters that prefix/suffix formatting takes up for the given LogItem
     * @param logItem the log item to apply prefixes and suffixes for
     * @return length of prefixes and suffixes
     */
    int getFormattingLength(LogItem logItem) {
        int length = 0;
        if (prefixer != null) length += prefixer.apply(logItem).length();
        if (suffixer != null) length += suffixer.apply(logItem).length();
        return length;
    }

}
