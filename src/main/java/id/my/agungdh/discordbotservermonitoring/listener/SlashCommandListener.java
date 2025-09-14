package id.my.agungdh.discordbotservermonitoring.listener;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class SlashCommandListener extends ListenerAdapter {
    public SlashCommandListener(net.dv8tion.jda.api.JDA jda) {
        jda.addEventListener(this);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "ping" -> event.reply("🏓 Pong!").setEphemeral(true).queue();
            case "echo" -> event.reply(event.getOption("text").getAsString()).queue();
            default -> event.reply("Unknown command 🤔").setEphemeral(true).queue();
        }
    }
}
