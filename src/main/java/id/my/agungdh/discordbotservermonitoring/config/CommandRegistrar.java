package id.my.agungdh.discordbotservermonitoring.config;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.stereotype.Component;

@Component
public class CommandRegistrar {
    private final JDA jda;

    public CommandRegistrar(JDA jda) {
        this.jda = jda;
    }

    @PostConstruct
    public void registerGlobalCommands() {
        var ping = Commands.slash("ping", "Cek latensi bot");
        var echo = Commands.slash("echo", "Balas teks")
                .addOption(OptionType.STRING, "text", "Teks", true);
        var health = Commands.slash("health", "Tampilkan kesehatan/monitoring server");
        var errors = Commands.slash("errors", "Tampilkan menit error utk beberapa periode");

        jda.updateCommands()
                .addCommands(ping, echo, health, errors)
                .queue(
                        ok -> System.out.println("Synced GLOBAL commands"),
                        Throwable::printStackTrace
                );
    }
}
