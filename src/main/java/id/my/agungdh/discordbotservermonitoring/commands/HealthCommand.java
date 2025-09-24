package id.my.agungdh.discordbotservermonitoring.commands;

import id.my.agungdh.discordbotservermonitoring.DTO.BlockListResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.SummaryResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.TopDomainsResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.service.MetricsService;
import id.my.agungdh.discordbotservermonitoring.service.PiHoleClient;
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
import java.util.concurrent.TimeUnit;

@Component
public class HealthCommand implements SlashCommand {

    private final MetricsService metricsService;
    private final PiHoleClient piHoleClient;

    public HealthCommand(MetricsService metricsService, PiHoleClient piHoleClient) {
        this.metricsService = metricsService;
        this.piHoleClient = piHoleClient;
    }

    @Override
    public String name() {
        return "health";
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        // Agar tidak timeout 3 detik
        event.deferReply()/* .setEphemeral(true) */.queue(hook -> {

            // Jalan paralel: metrics + semua data Pi-hole
            CompletableFuture<MetricsDTO> metricsFut = metricsService.snapshotAsync(true)
                    .orTimeout(5, TimeUnit.MINUTES);

            CompletableFuture<SummaryResponse> piholeSummaryFut = CompletableFuture.supplyAsync(() -> {
                try {
                    return piHoleClient.getSummary();
                } catch (Exception e) {
                    return null;
                }
            }).orTimeout(30, TimeUnit.SECONDS);

            CompletableFuture<TopDomainsResponse> topBlockedFut = CompletableFuture.supplyAsync(() -> {
                try {
                    return piHoleClient.getTopBlockedDomains(10);
                } catch (Exception e) {
                    return null;
                }
            }).orTimeout(30, TimeUnit.SECONDS);

            CompletableFuture<BlockListResponse> blockListsFut = CompletableFuture.supplyAsync(() -> {
                try {
                    return piHoleClient.getBlockLists();
                } catch (Exception e) {
                    return null;
                }
            }).orTimeout(30, TimeUnit.SECONDS);

            CompletableFuture.allOf(metricsFut, piholeSummaryFut, topBlockedFut, blockListsFut)
                    .orTimeout(7, TimeUnit.MINUTES)
                    .whenComplete((ignored, err) -> {
                        if (err != null) {
                            hook.editOriginal("⚠️ Gagal ambil data: " + err.getMessage()).queue();
                            return;
                        }

                        MetricsDTO m = metricsFut.getNow(null);
                        SummaryResponse s = piholeSummaryFut.getNow(null);
                        TopDomainsResponse tb = topBlockedFut.getNow(null);
                        BlockListResponse bl = blockListsFut.getNow(null);

                        if (m == null) {
                            hook.editOriginal("⚠️ Gagal ambil metrik server.").queue();
                            return;
                        }

                        // ===== 1) Summary embed =====
                        EmbedBuilder eb = new EmbedBuilder()
                                .setTitle("📊 Server Health")
                                .setColor(new Color(88, 101, 242))
                                .setTimestamp(Instant.now())
                                .setFooter("host: " + m.hostname() + " • uptime: " + MessageUtils.humanUptime(m.uptimeSeconds()));
                        eb.addField("OS", m.os(), true);
                        eb.addField("Time", m.timestamp().toString(), true);
                        eb.addBlankField(true);

                        // CPU
                        String cpuBar = MessageUtils.codeBlock(
                                MessageUtils.progressBar(m.cpu().cpuUsage()) + " " + MessageUtils.round2(m.cpu().cpuUsage()) + "%"
                        );
                        String cpuInfo = "**Model:** " + MessageUtils.safe(m.cpu().model()) + "\n" +
                                "**Cores:** " + m.cpu().physicalCores() + "p / " + m.cpu().logicalCores() + "l\n" +
                                "**Load1m:** " + (m.cpu().systemLoad1m() < 0 ? "N/A" : m.cpu().systemLoad1m()) + "\n" +
                                "**Temp:** " + (m.cpu().temperatureC() == null ? "N/A" : (m.cpu().temperatureC() + "°C"));
                        eb.addField("CPU " + MessageUtils.gaugeEmoji(m.cpu().cpuUsage()), cpuBar + "\n" + cpuInfo, false);

                        // Memory
                        String memBar = MessageUtils.codeBlock(
                                MessageUtils.progressBar(m.memory().usedPercent()) + " " + MessageUtils.round2(m.memory().usedPercent()) + "% (" +
                                        MessageUtils.humanBytes(m.memory().usedBytes()) + " / " + MessageUtils.humanBytes(m.memory().totalBytes()) + ")"
                        );
                        eb.addField("Memory", memBar, false);

                        // Swap (jika ada)
                        if (m.swap().totalBytes() > 0) {
                            String swapBar = MessageUtils.codeBlock(
                                    MessageUtils.progressBar(m.swap().usedPercent()) + " " + MessageUtils.round2(m.swap().usedPercent()) + "% (" +
                                            MessageUtils.humanBytes(m.swap().usedBytes()) + " / " + MessageUtils.humanBytes(m.swap().totalBytes()) + ")"
                            );
                            eb.addField("Swap", swapBar, false);
                        }

                        // ===== 2) Pi-hole summary =====
                        if (s != null && s.clients() != null && s.queries() != null) {
                            int active = s.clients().active();
                            int totalClients = s.clients().total();
                            long totalQ = s.queries().total();
                            long blockedQ = s.queries().blocked();
                            double pctBlocked = s.queries().percent_blocked();

                            eb.addBlankField(false);
                            eb.addField("Pi-hole Clients",
                                    "Active: `" + active + "`\n" +
                                            "Total: `" + totalClients + "`",
                                    true);

                            eb.addField("DNS Queries",
                                    "Total: `" + totalQ + "`\n" +
                                            "Blocked: `" + blockedQ + "` (" + MessageUtils.round2(pctBlocked) + "%)",
                                    true);

                            // ===== Gravity (relative time) =====
                            if (s.gravity() != null) {
                                long epoch = s.gravity().last_update();
                                String relative = "<t:" + epoch + ":R>";
                                eb.addField("Gravity",
                                        "Domains blocked: `" + s.gravity().domains_being_blocked() + "`\n" +
                                                "Last update: " + relative,
                                        false);
                            }
                        } else {
                            eb.addBlankField(false);
                            eb.addField("Pi-hole", "_unavailable_", false);
                        }

                        // ===== 3) Top 10 Blocked Only =====
                        if (tb != null && tb.domains() != null && !tb.domains().isEmpty()) {
                            StringBuilder topBlocked = new StringBuilder();
                            int i = 1;
                            for (var d : tb.domains()) {
                                topBlocked.append(i++)
                                        .append(". `").append(MessageUtils.safe(d.domain())).append("` ")
                                        .append("(").append(d.count()).append(")\n");
                            }
                            eb.addBlankField(false);
                            eb.addField("🚫 Top Blocked", topBlocked.toString(), true);
                        }

                        // ===== 4) Block Lists (type=block) =====
                        if (bl != null && bl.lists() != null && !bl.lists().isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (var item : bl.lists()) {
                                String updRel = "<t:" + item.date_updated() + ":R>";
                                sb.append("• ").append(MessageUtils.safe(item.address()))
                                        .append(" → `").append(item.number()).append("` entries")
                                        .append(" (upd ").append(updRel).append(")\n");
                            }
                            eb.addField("Block Lists", sb.toString(), false);
                        }

                        // ===== 5) Storage & Network pagination (dipotong 1800 char) =====
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

                        hook.editOriginalEmbeds(eb.build()).queue(v -> {
                            CompletableFuture<Void> flow = CompletableFuture.completedFuture(null);

                            if (!finalDiskParts.isEmpty()) {
                                flow = flow.thenCompose(ignored2 -> MessageUtils.sendSequentially(hook, finalDiskParts));
                            } else {
                                flow = flow.thenCompose(ignored2 -> MessageUtils.toCF(hook.sendMessage("_No storage info_"))
                                        .thenApply(x -> null));
                            }

                            flow.thenCompose(ignored2 -> {
                                        if (!finalNetParts.isEmpty()) {
                                            return MessageUtils.sendSequentially(hook, finalNetParts);
                                        } else {
                                            return MessageUtils.toCF(hook.sendMessage("_No network interface info_"))
                                                    .thenApply(x -> null);
                                        }
                                    })
                                    .exceptionally(ex -> {
                                        hook.sendMessage("⚠️ Gagal kirim data: " + ex.getMessage()).queue();
                                        return null;
                                    });
                        });
                    });
        });
    }
}
