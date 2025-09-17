package id.my.agungdh.discordbotservermonitoring.commands;


import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.service.MetricsService;
import id.my.agungdh.discordbotservermonitoring.util.MessageUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;


import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;


@Component
public class HealthCommand implements SlashCommand {


    private final MetricsService metricsService;


    public HealthCommand(MetricsService metricsService) {
        this.metricsService = metricsService;
    }


    @Override
    public String name() {
        return "health";
    }


    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply().queue();


        final MetricsDTO m;
        try {
            m = metricsService.snapshot(true);
        } catch (Exception e) {
            event.getHook().editOriginal("⚠️ Gagal ambil metrik: " + e.getMessage()).queue();
            return;
        }

        // ===== 1) Summary embed (ringkas) =====
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📊 Server Health")
                .setColor(new Color(88, 101, 242))
                .setTimestamp(Instant.now())
                .setFooter("host: " + m.hostname() + " • uptime: " + MessageUtils.humanUptime(m.uptimeSeconds()));
        eb.addField("OS", m.os(), true);
        eb.addField("Time", m.timestamp().toString(), true);
        eb.addBlankField(true);


        String cpuBar = MessageUtils.codeBlock(MessageUtils.progressBar(m.cpu().cpuUsage()) + " " + MessageUtils.round2(m.cpu().cpuUsage()) + "%");
        String cpuInfo = "**Model:** " + MessageUtils.safe(m.cpu().model()) + "\n" +
                "**Cores:** " + m.cpu().physicalCores() + "p / " + m.cpu().logicalCores() + "l\n" +
                "**Load1m:** " + (m.cpu().systemLoad1m() < 0 ? "N/A" : m.cpu().systemLoad1m()) + "\n" +
                "**Temp:** " + (m.cpu().temperatureC() == null ? "N/A" : (m.cpu().temperatureC() + "°C"));
        eb.addField("CPU " + MessageUtils.gaugeEmoji(m.cpu().cpuUsage()), cpuBar + "\n" + cpuInfo, false);


        String memBar = MessageUtils.codeBlock(
                MessageUtils.progressBar(m.memory().usedPercent()) + " " + MessageUtils.round2(m.memory().usedPercent()) + "% (" +
                        MessageUtils.humanBytes(m.memory().usedBytes()) + " / " + MessageUtils.humanBytes(m.memory().totalBytes()) + ")"
        );
        eb.addField("Memory", memBar, false);


        if (m.swap().totalBytes() > 0) {
            String swapBar = MessageUtils.codeBlock(
                    MessageUtils.progressBar(m.swap().usedPercent()) + " " + MessageUtils.round2(m.swap().usedPercent()) + "% (" +
                            MessageUtils.humanBytes(m.swap().usedBytes()) + " / " + MessageUtils.humanBytes(m.swap().totalBytes()) + ")"
            );
            eb.addField("Swap", swapBar, false);
        }

        // ===== 2) Siapkan halaman disk & network (dipotong 1800 char) =====
        List<String> diskParts = new ArrayList<>();
        if (m.storage() != null && !m.storage().isEmpty()) {
            StringBuilder all = new StringBuilder(4096);
            all.append("**Storage (all)**\n\n");
            int i = 1;
            for (var d : m.storage()) {
                all.append(String.format(
                        Locale.US,
                        "%d) `%s` • %s\n%s %s%% (%s / %s)\n\n",
                        i++,
                        MessageUtils.safe(d.name()), MessageUtils.safe(d.type()),
                        MessageUtils.progressBar(d.usedPercent()),
                        MessageUtils.round2(d.usedPercent()),
                        MessageUtils.humanBytes(d.totalBytes() - d.usableBytes()),
                        MessageUtils.humanBytes(d.totalBytes())
                ));
            }
            diskParts = MessageUtils.paginateLabeled("Storage", all.toString(), 1800);
        }


        List<String> netParts = new ArrayList<>();
        if (m.networks() != null && !m.networks().isEmpty()) {
            StringBuilder all = new StringBuilder(4096);
            all.append("**Network Interfaces (all)**\n\n");
            int i = 1;
            for (var nif : m.networks()) {
                String ipv4 = (nif.ipv4() == null || nif.ipv4().isBlank()) ? "-" : nif.ipv4();
                String ipv6 = (nif.ipv6() == null || nif.ipv6().isBlank()) ? "-" : nif.ipv6();
                all.append(String.format(
                        Locale.US,
                        "%d) `%s` • %s\nIPv4: %s | IPv6: %s\n↓ %s • ↑ %s\n\n",
                        i++,
                        MessageUtils.safe(nif.name()), MessageUtils.safe(nif.mac()),
                        ipv4, ipv6,
                        MessageUtils.humanBytes(nif.bytesRecv()),
                        MessageUtils.humanBytes(nif.bytesSent())
                ));
            }
            netParts = MessageUtils.paginateLabeled("Network Interfaces", all.toString(), 1800);
        }

        final List<String> finalDiskParts = MessageUtils.toCodeBlocks(diskParts);
        final List<String> finalNetParts = MessageUtils.toCodeBlocks(netParts);

        event.getHook().editOriginalEmbeds(eb.build()).queue(v -> {
            CompletableFuture<Void> flow = CompletableFuture.completedFuture(null);


            if (!finalDiskParts.isEmpty()) {
                flow = flow.thenCompose(ignored -> MessageUtils.sendSequentially(event.getHook(), finalDiskParts));
            } else {
                flow = flow.thenCompose(ignored -> MessageUtils.toCF(event.getHook().sendMessage("_No storage info_"))
                        .thenApply(x -> null));
            }


            flow.thenCompose(ignored -> {
                        if (!finalNetParts.isEmpty()) {
                            return MessageUtils.sendSequentially(event.getHook(), finalNetParts);
                        } else {
                            return MessageUtils.toCF(event.getHook().sendMessage("_No network interface info_"))
                                    .thenApply(x -> null);
                        }
                    })
                    .exceptionally(ex -> {
                        event.getHook().sendMessage("⚠️ Gagal kirim data: " + ex.getMessage()).queue();
                        return null;
                    });
        });
    }
}