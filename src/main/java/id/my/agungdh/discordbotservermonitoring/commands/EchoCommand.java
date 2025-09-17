package id.my.agungdh.discordbotservermonitoring.commands;


import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;


@Component
public class EchoCommand implements SlashCommand {


    @Override
    public String name() {
        return "echo";
    }


    @Override
    public void handle(SlashCommandInteractionEvent event) {
        var opt = event.getOption("text");
        if (opt == null) {
            event.reply("(no text)").setEphemeral(true).queue();
            return;
        }
        event.reply(opt.getAsString()).queue();
    }
}