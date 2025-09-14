package id.my.agungdh.discordbotservermonitoring.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommandRegistrar extends ListenerAdapter {
    private final JDA jda;
    private final String guildId;

    public CommandRegistrar(JDA jda, @Value("${discord.guild-id:}") String guildId) {
        this.jda = jda;
        this.guildId = guildId;
        jda.addEventListener(this);
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        var ping = Commands.slash("ping", "Cek latensi bot");
        var echo = Commands.slash("echo", "Balas teks").addOption(
                net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "text", "Teks", true
        );

        if (guildId != null && !guildId.isBlank()) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) guild.updateCommands().addCommands(ping, echo).queue();
            else System.err.println("Guild ID tidak ditemukan: " + guildId);
        } else {
            jda.updateCommands().addCommands(ping, echo).queue(); // global (propagasi beberapa menit)
        }
    }
}

