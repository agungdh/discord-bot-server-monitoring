package id.my.agungdh.discordbotservermonitoring.config;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CommandRegistrar {
    private final JDA jda;

    public CommandRegistrar(JDA jda) {
        this.jda = jda;
    }

    @PostConstruct
    public void registerGlobalCommands() {
        var ping   = Commands.slash("ping", "Cek latensi bot");
        var echo   = Commands.slash("echo", "Balas teks")
                .addOption(OptionType.STRING, "text", "Teks", true);
        var health = Commands.slash("health", "Tampilkan kesehatan/monitoring server");

        jda.updateCommands()
                .addCommands(ping, echo, health)   // replace penuh daftar command global
                .queue(
                        ok -> System.out.println("Synced GLOBAL commands"),
                        Throwable::printStackTrace
                );
    }
}
