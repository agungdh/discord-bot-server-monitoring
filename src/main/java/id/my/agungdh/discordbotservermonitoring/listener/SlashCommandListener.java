package id.my.agungdh.discordbotservermonitoring.listener;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.service.MetricsService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Component
public class SlashCommandListener extends ListenerAdapter {

    private final MetricsService metricsService;

    public SlashCommandListener(net.dv8tion.jda.api.JDA jda, MetricsService metricsService) {
        this.metricsService = metricsService;
        jda.addEventListener(this);
    }

    private static String codeBlock(String s) {
        return "```" + s + "```";
    }

    /* ====================== Helpers ====================== */

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static List<String> chunkString(String s, int maxLen) {
        List<String> parts = new ArrayList<>();
        String remaining = s;
        while (remaining.length() > maxLen) {
            int cut = maxLen;
            int nl = remaining.lastIndexOf('\n', maxLen);
            if (nl > 0) cut = nl;
            parts.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut);
        }
        if (!remaining.isBlank()) parts.add(remaining);
        return parts;
    }

    private static List<String> paginateLabeled(String title, String content, int pageLen) {
        List<String> chunks = chunkString(content, pageLen);
        List<String> labeled = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            labeled.add("**" + title + " (Page " + (i + 1) + "/" + chunks.size() + ")**\n\n" + chunks.get(i));
        }
        return labeled;
    }

    private static List<String> toCodeBlocks(List<String> rawParts) {
        List<String> blocks = new ArrayList<>(rawParts.size());
        for (String p : rawParts) blocks.add(codeBlock(p));
        return blocks;
    }

    // Kirim berantai agar urutan pasti
    private static CompletableFuture<Void> sendSequentially(
            net.dv8tion.jda.api.interactions.InteractionHook hook,
            List<String> messages
    ) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String msg : messages) {
            chain = chain.thenCompose(ignored -> toCF(hook.sendMessage(msg)).thenApply(x -> null));
        }
        return chain;
    }

    private static <T> CompletableFuture<T> toCF(net.dv8tion.jda.api.requests.RestAction<T> ra) {
        var cf = new CompletableFuture<T>();
        ra.queue(cf::complete, cf::completeExceptionally);
        return cf;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] u = {"KB", "MB", "GB", "TB", "PB"};
        int i = -1;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.US, "%.2f %s", v, u[i]);
    }

    private static String progressBar(double percent) {
        int filled = (int) Math.round(Math.max(0, Math.min(100, percent)) / 10.0);
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "█" : "░");
        return sb.toString();
    }

    private static String humanUptime(long seconds) {
        Duration d = Duration.ofSeconds(Math.max(0, seconds));
        long days = d.toDays();
        d = d.minusDays(days);
        long hours = d.toHours();
        d = d.minusHours(hours);
        long mins = d.toMinutes();
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        sb.append(mins).append("m");
        return sb.toString();
    }

    private static String gaugeEmoji(double percent) {
        if (percent >= 90) return "🟥";
        if (percent >= 75) return "🟧";
        if (percent >= 50) return "🟨";
        return "🟩";
    }

    private static String round2(double v) {
        return String.format(Locale.US, "%.2f", v);
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
                        .setFooter("host: " + m.hostname() + " • uptime: " + humanUptime(m.uptimeSeconds()));
                eb.addField("OS", m.os(), true);
                eb.addField("Time", m.timestamp().toString(), true);
                eb.addBlankField(true);

                String cpuBar = codeBlock(progressBar(m.cpu().cpuUsage()) + " " + round2(m.cpu().cpuUsage()) + "%");
                String cpuInfo = "**Model:** " + safe(m.cpu().model()) + "\n" +
                        "**Cores:** " + m.cpu().physicalCores() + "p / " + m.cpu().logicalCores() + "l\n" +
                        "**Load1m:** " + (m.cpu().systemLoad1m() < 0 ? "N/A" : m.cpu().systemLoad1m()) + "\n" +
                        "**Temp:** " + (m.cpu().temperatureC() == null ? "N/A" : (m.cpu().temperatureC() + "°C"));
                eb.addField("CPU " + gaugeEmoji(m.cpu().cpuUsage()), cpuBar + "\n" + cpuInfo, false);

                String memBar = codeBlock(
                        progressBar(m.memory().usedPercent()) + " " + round2(m.memory().usedPercent()) + "% (" +
                                humanBytes(m.memory().usedBytes()) + " / " + humanBytes(m.memory().totalBytes()) + ")"
                );
                eb.addField("Memory", memBar, false);

                if (m.swap().totalBytes() > 0) {
                    String swapBar = codeBlock(
                            progressBar(m.swap().usedPercent()) + " " + round2(m.swap().usedPercent()) + "% (" +
                                    humanBytes(m.swap().usedBytes()) + " / " + humanBytes(m.swap().totalBytes()) + ")"
                    );
                    eb.addField("Swap", swapBar, false);
                }

                // ===== 2) Siapkan semua halaman disk & network (dipotong 1800 char biar aman) =====
                List<String> diskParts = new ArrayList<>();
                if (m.storage() != null && !m.storage().isEmpty()) {
                    StringBuilder all = new StringBuilder(4096);
                    all.append("**Storage (all)**\n\n");
                    int i = 1;
                    for (var d : m.storage()) {
                        all.append(String.format(
                                Locale.US,
                                "%d) `%s` • %s\n%s %s%%  (%s / %s)\n\n",
                                i++,
                                safe(d.name()), safe(d.type()),
                                progressBar(d.usedPercent()),
                                round2(d.usedPercent()),
                                humanBytes(d.totalBytes() - d.usableBytes()),
                                humanBytes(d.totalBytes())
                        ));
                    }
                    diskParts = paginateLabeled("Storage", all.toString(), 1800);
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
                                safe(nif.name()), safe(nif.mac()),
                                ipv4, ipv6,
                                humanBytes(nif.bytesRecv()),
                                humanBytes(nif.bytesSent())
                        ));
                    }
                    netParts = paginateLabeled("Network Interfaces", all.toString(), 1800);
                }

                // ===== 3) Kirim BERURUTAN: summary -> disk parts -> net parts =====
                final List<String> finalDiskParts = toCodeBlocks(diskParts);
                final List<String> finalNetParts = toCodeBlocks(netParts);

                event.getHook().editOriginalEmbeds(eb.build()).queue(v -> {
                    CompletableFuture<Void> flow = CompletableFuture.completedFuture(null);

                    if (!finalDiskParts.isEmpty()) {
                        flow = flow.thenCompose(ignored -> sendSequentially(event.getHook(), finalDiskParts));
                    } else {
                        flow = flow.thenCompose(ignored -> toCF(event.getHook().sendMessage("_No storage info_")).thenApply(x -> null));
                    }

                    flow.thenCompose(ignored -> {
                                if (!finalNetParts.isEmpty()) {
                                    return sendSequentially(event.getHook(), finalNetParts);
                                } else {
                                    return toCF(event.getHook().sendMessage("_No network interface info_")).thenApply(x -> null);
                                }
                            })
                            .exceptionally(ex -> {
                                event.getHook().sendMessage("⚠️ Gagal kirim data: " + ex.getMessage()).queue();
                                return null;
                            });
                });
            }

            default -> event.reply("Unknown command 🤔").setEphemeral(true).queue();
        }
    }
}
