import me.scarsz.jdaappender.ChannelLoggingHandler;
import me.scarsz.jdaappender.ExtensionBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;

public class CustomPrefixExample {

    public static void main(String[] args) throws LoginException {
        // initialize JDA
        JDA jda = JDABuilder.createDefault(System.getenv("TOKEN")).build();

        // initialize logging handler
        ChannelLoggingHandler handler = new ChannelLoggingHandler(() -> jda.getTextChannelById(System.getenv("CHANNEL")), handlerConfig -> {
            handlerConfig.setColored(true);
            handlerConfig.setSplitCodeBlockForLinks(false);
            handlerConfig.setAllowLinkEmbeds(true);
            handlerConfig.mapLoggerName("net.dv8tion.jda", "JDA");
            handlerConfig.setPrefixer(new ExtensionBuilder(handlerConfig)
                    .date().space().time12Hours().space()
                    .text("[").levelPadded().space().loggerPadded().text("]")
                    .build()
            );
        }).attach().schedule();

        // at this point, logging is ready to go and will be streamed to the channel
    }

}
