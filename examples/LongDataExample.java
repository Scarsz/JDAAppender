import me.scarsz.jdaappender.ChannelLoggingHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;
import java.io.PrintStream;
import java.util.concurrent.ThreadLocalRandom;

public class LongDataExample {

    public static void main(String[] args) throws LoginException {
        // initialize JDA
        JDA jda = JDABuilder.createDefault(System.getenv("TOKEN")).build();

        // initialize logging handler
        ChannelLoggingHandler handler = new ChannelLoggingHandler(() -> jda.getTextChannelById(System.getenv("CHANNEL")), handlerConfig -> {
            handlerConfig.setColored(true);
            handlerConfig.setSplitCodeBlockForLinks(false);
            handlerConfig.setAllowLinkEmbeds(true);
            handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
        }).attachSystemLogging().schedule();

        // enqueue long random, log messages
        int count = ThreadLocalRandom.current().nextInt(5, 15);
        for (int i = 0; i < count; i++) {
            String randomCharacters = randomAlphanumeric(100, 10000);
            PrintStream stream = ThreadLocalRandom.current().nextDouble(1) > .5 ? System.out : System.err;
            stream.println(
                    "Test message " + (i + 1) + ": data[" + randomCharacters.length() + "] " + randomCharacters
            );
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
