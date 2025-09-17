package id.my.agungdh.discordbotservermonitoring.commands;


import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;


@Component
public class PingCommand implements SlashCommand {


    @Override
    public String name() { return "ping"; }


    @Override
    public void handle(SlashCommandInteractionEvent event) {
        long start = System.currentTimeMillis();
        event.reply("⏱️ Pinging...").setEphemeral(true).queue(hook -> {
            long latency = System.currentTimeMillis() - start;
            hook.editOriginal("🏓 Pong! Latency ~ **" + latency + " ms** (gateway: " + event.getJDA().getGatewayPing() + " ms)").queue();
        });
    }
}