package id.my.agungdh.discordbotservermonitoring.commands;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.pihole.BlockListResponse;
import id.my.agungdh.discordbotservermonitoring.DTO.pihole.SummaryResponse;
import id.my.agungdh.discordbotservermonitoring.service.NodeMetricsService;
import id.my.agungdh.discordbotservermonitoring.service.PiHoleClient;
import id.my.agungdh.discordbotservermonitoring.util.MessageUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class HealthCommand implements SlashCommand {

    private final NodeMetricsService nodeMetricsService;
    private final PiHoleClient piHoleClient;

    public HealthCommand(NodeMetricsService nodeMetricsService, PiHoleClient piHoleClient) {
        this.nodeMetricsService = nodeMetricsService;
        this.piHoleClient = piHoleClient;
    }

    @Override
    public String name() {
        return "health";
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply()/* .setEphemeral(true) */.queue(hook -> {

            // Ambil semua node paralel + Pi-hole
            CompletableFuture<Map<String, MetricsDTO>> nodesFut = nodeMetricsService.snapshotAllAsync(true)
                    .orTimeout(5, TimeUnit.MINUTES);

            CompletableFuture<SummaryResponse> piholeSummaryFut = CompletableFuture.supplyAsync(() -> {
                try {
                    return piHoleClient.getSummary();
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

            CompletableFuture.allOf(nodesFut, piholeSummaryFut, blockListsFut)
                    .orTimeout(7, TimeUnit.MINUTES)
                    .whenComplete((ignored, err) -> {
                        if (err != null) {
                            hook.editOriginal("⚠️ Gagal ambil data: " + err.getMessage()).queue();
                            return;
                        }

                        Map<String, MetricsDTO> nodes = nodesFut.getNow(Map.of());
                        SummaryResponse s = piholeSummaryFut.getNow(null);
                        BlockListResponse bl = blockListsFut.getNow(null);

                        // 1) Kirim embed PI-HOLE terlebih dahulu
                        EmbedBuilder piHoleEb = buildPiHoleEmbed(s, bl);
                        hook.editOriginalEmbeds(piHoleEb.build()).queue(v -> {

                            // 2) Lalu kirim embed per NODE
                            if (nodes.isEmpty()) {
                                hook.sendMessage("⚠️ Tidak ada data node.").queue();
                                return;
                            }

                            var ordered = new ArrayList<>(nodes.entrySet());
                            ordered.sort(Map.Entry.comparingByKey());

                            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

                            for (int idx = 0; idx < ordered.size(); idx++) {
                                var eNode = ordered.get(idx);
                                String nodeName = eNode.getKey();
                                MetricsDTO m = eNode.getValue();

                                // Embed untuk node
                                EmbedBuilder eb = buildNodeEmbed(nodeName, m);
                                chain = chain.thenCompose(ignored2 -> MessageUtils.toCF(hook.sendMessageEmbeds(eb.build()))
                                        .thenApply(x -> null));

                                // Pagination Storage & Network
                                List<String> diskParts = buildStoragePages(m);
                                List<String> netParts  = buildNetworkPages(m);

                                chain = chain.thenCompose(ignored2 -> sendParts(hook, diskParts, "_" + nodeName + " • No storage info_"));
                                chain = chain.thenCompose(ignored2 -> sendParts(hook, netParts, "_" + nodeName + " • No network interface info_"));
                            }

                            chain.exceptionally(ex -> {
                                hook.sendMessage("⚠️ Gagal kirim data: " + ex.getMessage()).queue();
                                return null;
                            });
                        });
                    });
        });
    }

    // ================== Builders ==================

    private static EmbedBuilder buildPiHoleEmbed(SummaryResponse s, BlockListResponse bl) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🕳️ Pi-hole")
                .setColor(new Color(34, 197, 94))
                .setTimestamp(Instant.now());

        if (s != null && s.clients() != null && s.queries() != null) {
            int active = s.clients().active();
            int totalClients = s.clients().total();
            long totalQ = s.queries().total();
            long blockedQ = s.queries().blocked();
            double pctBlocked = s.queries().percent_blocked();

            eb.addField("Clients",
                    "Active: `" + active + "`\n" +
                            "Total: `" + totalClients + "`",
                    true);

            eb.addField("DNS Queries",
                    "Total: `" + totalQ + "`\n" +
                            "Blocked: `" + blockedQ + "` (" + MessageUtils.round2(pctBlocked) + "%)",
                    true);

            if (s.gravity() != null) {
                long epoch = s.gravity().last_update();
                String relative = "<t:" + epoch + ":R>";
                eb.addField("Gravity",
                        "Domains blocked: `" + s.gravity().domains_being_blocked() + "`\n" +
                                "Last update: " + relative,
                        false);
            }
        } else {
            eb.addField("Summary", "_unavailable_", false);
        }

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

        return eb;
    }

    private static EmbedBuilder buildNodeEmbed(String nodeName, MetricsDTO m) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📊 Server Health — " + nodeName)
                .setColor(new Color(88, 101, 242))
                .setTimestamp(Instant.now())
                .setFooter("host: " + m.hostname() + " • uptime: " + MessageUtils.humanUptime(m.uptimeSeconds()));

        eb.addField("OS", MessageUtils.safe(m.os()), true);
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

        return eb;
    }

    // ================== Pagination builders ==================

    private static List<String> buildStoragePages(MetricsDTO m) {
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
        return MessageUtils.toCodeBlocks(diskParts);
    }

    private static List<String> buildNetworkPages(MetricsDTO m) {
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
        return MessageUtils.toCodeBlocks(netParts);
    }

    // ================== Utils ==================

    private static CompletableFuture<Void> sendParts(net.dv8tion.jda.api.interactions.InteractionHook hook,
                                                     List<String> parts,
                                                     String emptyFallback) {
        if (!parts.isEmpty()) {
            return MessageUtils.sendSequentially(hook, parts);
        } else {
            return MessageUtils.toCF(hook.sendMessage(emptyFallback)).thenApply(x -> null);
        }
    }
}
