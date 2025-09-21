package id.my.agungdh.discordbotservermonitoring.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class EchoCommand implements SlashCommand {

    private final Executor executor;

    public EchoCommand(@Qualifier("commandExecutor") Executor executor) {
        this.executor = executor;
    }

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        // jalankan logic di thread pool supaya non-blocking
        CompletableFuture.runAsync(() -> {
            var opt = event.getOption("text");
            if (opt == null) {
                event.reply("(no text)").queue();
            } else {
                event.reply(opt.getAsString()).queue();
            }
        }, executor);
    }
}
