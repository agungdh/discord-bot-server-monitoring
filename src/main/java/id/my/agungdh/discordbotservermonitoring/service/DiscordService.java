package id.my.agungdh.discordbotservermonitoring.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.File;
import java.time.Instant;

@Service
public class DiscordService {

    private final JDA jda;

    public DiscordService(JDA jda) {
        this.jda = jda;
    }

    public void sendMessage(String guildId, String channelId, String content) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) throw new IllegalArgumentException("Guild " + guildId + " tidak ditemukan");
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null)
            throw new IllegalArgumentException("Channel " + channelId + " tidak ditemukan di guild " + guildId);

        channel.sendMessage(content).queue();
    }

    public void sendAlertEmbed(String guildId, String channelId,
                               String instance, String alias, double failsPerMinute, Instant ts) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) throw new IllegalArgumentException("Guild " + guildId + " tidak ditemukan");
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null)
            throw new IllegalArgumentException("Channel " + channelId + " tidak ditemukan di guild " + guildId);

        var safeAlias = (alias == null || alias.isBlank()) ? "-" : alias;
        var color = failsPerMinute >= 10 ? Color.RED : Color.ORANGE;
        var emoji = failsPerMinute >= 10 ? "🛑" : "⚠️";

        var embed = new EmbedBuilder()
                .setColor(color)
                .setTitle(emoji + " PING ALERT")
                .addField("Instance", "`" + instance + "`", true)
                .addField("Alias", "`" + safeAlias + "`", true)
                .addField("Gagal/1m", "`" + (int) failsPerMinute + "`", true)
                .setTimestamp(ts)
                .build();

        channel.sendMessageEmbeds(embed).queue();
    }

    /**
     * Upload file PNG dsb dengan caption
     */
    public void sendFile(String guildId, String channelId, File file, String caption) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) throw new IllegalArgumentException("Guild " + guildId + " tidak ditemukan");
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null)
            throw new IllegalArgumentException("Channel " + channelId + " tidak ditemukan di guild " + guildId);

        if (caption == null || caption.isBlank()) {
            channel.sendFiles(FileUpload.fromData(file, file.getName())).queue();
        } else {
            channel.sendMessage(caption)
                    .addFiles(FileUpload.fromData(file, file.getName()))
                    .queue();
        }
    }
}
