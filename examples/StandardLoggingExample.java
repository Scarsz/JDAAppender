import me.scarsz.jdaappender.ChannelLoggingHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;

public class StandardLoggingExample {

    public static void main(String[] args) throws LoginException, InterruptedException {
        // initialize JDA
        JDA jda = JDABuilder.createDefault(System.getenv("TOKEN")).build();

        // initialize logging handler
        ChannelLoggingHandler handler = new ChannelLoggingHandler(() -> jda.getTextChannelById(System.getenv("CHANNEL")), handlerConfig -> {
            handlerConfig.setColored(true);
            handlerConfig.setSplitCodeBlockForLinks(false);
            handlerConfig.setAllowLinkEmbeds(true);
            handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
        }).attachSystemLogging().schedule();

        // at this point, System.out/err logging is ready to go and will be streamed to the channel

        System.out.println("Hello world");
        System.err.println("Goodbye world");

        jda.awaitReady(); // wait for JDA to fully finish loading
        handler.flush(); // flush the previous sout/serr messages to the channel
        System.exit(0);
    }

}
