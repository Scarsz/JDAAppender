package me.your.app;

import me.scarsz.jdaappender.ChannelLoggingHandler;
import me.scarsz.jdaappender.LogItem;
import me.scarsz.jdaappender.LogLevel;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;
import java.util.concurrent.ThreadLocalRandom;

public class RandomDataExample {

    public static void main(String[] args) throws LoginException, InterruptedException {
        // initialize JDA
        JDA jda = JDABuilder.createDefault(System.getenv("TOKEN")).build();

        // initialize logging handler
        ChannelLoggingHandler handler = new ChannelLoggingHandler(() -> jda.getTextChannelById(System.getenv("CHANNEL")), handlerConfig -> {
            handlerConfig.setColored(true);
            handlerConfig.setSplitCodeBlockForLinks(false);
            handlerConfig.setAllowLinkEmbeds(true);
            handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
        }).attachJavaLogging().schedule();

        // enqueue a bunch of random log messages
        int count = ThreadLocalRandom.current().nextInt(100, 500);
        for (int i = 0; i < count; i++) {
            double random = ThreadLocalRandom.current().nextDouble(1);
            String randomCharacters = randomAlphanumeric(100, 250);

            handler.enqueue(new LogItem(
                    "Logger name here",
                    System.currentTimeMillis(),
                    random < .5 ? LogLevel.INFO : random < .8 ? LogLevel.WARN : LogLevel.ERROR,
                    "Test message " + (i + 1) + ": " + (ThreadLocalRandom.current().nextDouble() > .9
                            ? "https://lorem.ipsum/#" + randomCharacters.substring(0, ThreadLocalRandom.current().nextInt(100))
                            : "data[" + randomCharacters.length() + "] " + randomCharacters),
                    null
            ));
            if (ThreadLocalRandom.current().nextDouble() < .5) {
                Thread.sleep(1000);
            }
        }
    }

    private static String randomAlphanumeric(int lengthLowerBound, int lengthUpperBound) {
        return ThreadLocalRandom.current().ints(48, 122 + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(ThreadLocalRandom.current().nextInt(lengthLowerBound, lengthUpperBound))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

}
