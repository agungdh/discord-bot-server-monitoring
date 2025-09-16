package id.my.agungdh.discordbotservermonitoring.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;

@Service
public class DiscordService {

    private final JDA jda;

    public DiscordService(JDA jda) {
        this.jda = jda;
    }

    public void sendMessage(String guildId, String channelId, String content) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Guild dengan ID " + guildId + " tidak ditemukan");
        }

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel dengan ID " + channelId + " tidak ditemukan di guild " + guildId);
        }

        channel.sendMessage(content).queue();
    }
}
