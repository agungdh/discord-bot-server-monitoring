package id.my.agungdh.discordbotservermonitoring.listener;


import id.my.agungdh.discordbotservermonitoring.commands.SlashCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
public class SlashCommandRouter extends ListenerAdapter {


    private final Map<String, SlashCommand> handlersByName;


    public SlashCommandRouter(JDA jda, List<SlashCommand> handlers) {
        this.handlersByName = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(SlashCommand::name, h -> h));
        jda.addEventListener(this);
    }


    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        SlashCommand handler = handlersByName.get(event.getName());
        if (handler != null) {
            handler.handle(event);
        } else {
            event.reply("Unknown command 🤔").setEphemeral(true).queue();
        }
    }
}