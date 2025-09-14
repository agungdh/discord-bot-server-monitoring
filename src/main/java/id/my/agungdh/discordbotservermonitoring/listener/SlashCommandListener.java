package id.my.agungdh.discordbotservermonitoring.listener;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.StorageDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.NetworkDTO;
import id.my.agungdh.discordbotservermonitoring.service.MetricsService;
import id.my.agungdh.discordbotservermonitoring.view.MonitoringBlocks; // pakai helper humanBytes(), dll (opsional)

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class SlashCommandListener extends ListenerAdapter {

    private final MetricsService metricsService;

    public SlashCommandListener(net.dv8tion.jda.api.JDA jda, MetricsService metricsService) {
        this.metricsService = metricsService;
        jda.addEventListener(this);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "ping" -> {
                long start = System.currentTimeMillis();
                event.reply("⏱️ Pinging...").setEphemeral(true).queue(hook -> {
                    long latency = System.currentTimeMillis() - start;
                    hook.editOriginal("🏓 Pong! Latency ~ **" + latency + " ms** (gateway: " + event.getJDA().getGatewayPing() + " ms)").queue();
                });
            }

            case "echo" -> event.reply(event.getOption("text").getAsString()).queue();

            case "health" -> {
                // kalau hitungannya agak berat, pakai deferReply biar UX mulus
                event.deferReply().queue();

                MetricsDTO m = metricsService.snapshot(true);

                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("📊 Server Health")
                        .setColor(new Color(88, 101, 242))
                        .setTimestamp(Instant.now())
                        .setFooter("host: " + m.hostname() + " • uptime: " + MonitoringBlocks.humanUptime(m.uptimeSeconds()));

                // Header ringkas
                eb.addField("OS", m.os(), true);
                eb.addField("Time", m.timestamp().toString(), true);
                eb.addBlankField(true);

                // CPU
                String cpuBar = codeBlock(
                        MonitoringBlocks.progressBar(m.cpu().cpuUsage()) + " " + MonitoringBlocks.round2(m.cpu().cpuUsage()) + "%"
                );
                String cpuInfo =
                        "**Model:** " + safe(m.cpu().model()) + "\n" +
                                "**Cores:** " + m.cpu().physicalCores() + "p / " + m.cpu().logicalCores() + "l\n" +
                                "**Load1m:** " + (m.cpu().systemLoad1m() < 0 ? "N/A" : m.cpu().systemLoad1m()) + "\n" +
                                "**Temp:** " + (m.cpu().temperatureC() == null ? "N/A" : (m.cpu().temperatureC() + "°C"));
                eb.addField("CPU " + MonitoringBlocks.gaugeEmoji(m.cpu().cpuUsage()), cpuBar + "\n" + cpuInfo, false);

                // Memory
                String memBar = codeBlock(
                        MonitoringBlocks.progressBar(m.memory().usedPercent()) + " " + MonitoringBlocks.round2(m.memory().usedPercent()) + "% (" +
                                MonitoringBlocks.humanBytes(m.memory().usedBytes()) + " / " + MonitoringBlocks.humanBytes(m.memory().totalBytes()) + ")"
                );
                eb.addField("Memory", memBar, false);

                // Swap (jika ada)
                if (m.swap().totalBytes() > 0) {
                    String swapBar = codeBlock(
                            MonitoringBlocks.progressBar(m.swap().usedPercent()) + " " + MonitoringBlocks.round2(m.swap().usedPercent()) + "% (" +
                                    MonitoringBlocks.humanBytes(m.swap().usedBytes()) + " / " + MonitoringBlocks.humanBytes(m.swap().totalBytes()) + ")"
                    );
                    eb.addField("Swap", swapBar, false);
                }

                // Storage (tampilkan top 3 by used%)
                if (m.storage() != null && !m.storage().isEmpty()) {
                    List<StorageDTO> top = m.storage().stream()
                            .sorted(Comparator.comparingDouble(StorageDTO::usedPercent).reversed())
                            .limit(3)
                            .toList();

                    StringBuilder sb = new StringBuilder();
                    for (StorageDTO d : top) {
                        String line = "`" + safe(d.name()) + "` • " + safe(d.type()) + "\n" +
                                codeBlock(MonitoringBlocks.progressBar(d.usedPercent()) + " " +
                                        MonitoringBlocks.round2(d.usedPercent()) + "% (" +
                                        MonitoringBlocks.humanBytes(d.totalBytes() - d.usableBytes()) + " / " +
                                        MonitoringBlocks.humanBytes(d.totalBytes()) + ")");
                        sb.append(line).append("\n");
                    }
                    eb.addField("Storage (Top 3)", sb.toString(), false);
                }

                // Network (ringkas)
                if (m.networks() != null && !m.networks().isEmpty()) {
                    int count = m.networks().size();
                    NetworkDTO first = m.networks().get(0);
                    String netText = "**Interfaces:** " + count + "\n" +
                            "`" + safe(first.name()) + "` • " + safe(first.mac()) + "\n" +
                            "IPv4: " + (isBlank(first.ipv4()) ? "-" : first.ipv4()) + " | IPv6: " + (isBlank(first.ipv6()) ? "-" : first.ipv6());
                    eb.addField("Network", netText, false);
                }

                event.getHook().editOriginalEmbeds(eb.build()).queue();
            }

            default -> event.reply("Unknown command 🤔").setEphemeral(true).queue();
        }
    }

    private static String codeBlock(String s) { return "```" + s + "```"; }
    private static String safe(String s) { return s == null ? "" : s; }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
