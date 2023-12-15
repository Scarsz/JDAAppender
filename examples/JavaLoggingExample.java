import me.scarsz.jdaappender.ChannelLoggingHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;

public class JavaLoggingExample {

    public static void main(String[] args) throws LoginException {
        // initialize JDA
        JDA jda = JDABuilder.createDefault(System.getenv("TOKEN")).build();

        // initialize logging handler
        ChannelLoggingHandler handler = new ChannelLoggingHandler(() -> jda.getTextChannelById(System.getenv("CHANNEL")), handlerConfig -> {
            handlerConfig.setColored(true);
            handlerConfig.setSplitCodeBlockForLinks(false);
            handlerConfig.setAllowLinkEmbeds(true);
            handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
        }).attachJavaLogging().schedule();

        // at this point, java.util.logging is ready to go and will be streamed to the channel

        // to direct SLF4J messages to java.util.logging, add a dependency for slf4j:jdk14, and it'll just work
        // <dependency>
        //     <groupId>org.slf4j</groupId>
        //     <artifactId>slf4j-jdk14</artifactId>
        //     <version>1.7.31</version>
        // </dependency>
    }

}
