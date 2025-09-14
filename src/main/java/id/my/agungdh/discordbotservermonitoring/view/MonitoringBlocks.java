package id.my.agungdh.discordbotservermonitoring.view;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.NetworkDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.StorageDTO;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Satu-satunya tempat yang membangun tampilan monitoring:
 * - buildSdkBlocks  : untuk Slack Web API (chat.postMessage) -> List<LayoutBlock>
 * - buildJsonBlocks : untuk respon Slash Command (HTTP 200 body) -> List<Map<String,Object>>
 * Semua helper (format bytes, progress bar, dll) ada di sini agar tidak duplikat.
 */
public final class MonitoringBlocks {

    private MonitoringBlocks() {
    }

    /* ====================== Public APIs ====================== */

    // Dipakai Controller slash (JSON Maps untuk HTTP response)
    public static List<Map<String, Object>> buildJsonBlocks(MetricsDTO m) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        // header
        blocks.add(Map.of(
                "type", "header",
                "text", Map.of("type", "plain_text", "text", "📊 Server Monitoring")
        ));

        // context
        blocks.add(Map.of(
                "type", "context",
                "elements", List.of(
                        Map.of("type", "mrkdwn", "text", "*Host:* `" + m.hostname() + "`"),
                        Map.of("type", "mrkdwn", "text", "*OS:* " + m.os()),
                        Map.of("type", "mrkdwn", "text", "*Uptime:* " + humanUptime(m.uptimeSeconds())),
                        Map.of("type", "mrkdwn", "text", "_" + m.timestamp() + "_")
                )
        ));

        blocks.add(Map.of("type", "divider"));

        // CPU
        String cpuLine = "*CPU* " + gaugeEmoji(m.cpu().cpuUsage()) + "\n" +
                "```" + progressBar(m.cpu().cpuUsage()) + " " + round2(m.cpu().cpuUsage()) + "%```";
        List<Map<String, Object>> cpuFields = List.of(
                Map.of("type", "mrkdwn", "text", "*Model:*\n" + safe(m.cpu().model())),
                Map.of("type", "mrkdwn", "text", "*Cores:*\n" + m.cpu().physicalCores() + "p / " + m.cpu().logicalCores() + "l"),
                Map.of("type", "mrkdwn", "text", "*Load 1m:*\n" + (m.cpu().systemLoad1m() < 0 ? "N/A" : m.cpu().systemLoad1m())),
                Map.of("type", "mrkdwn", "text", "*Temp:*\n" + (m.cpu().temperatureC() == null ? "N/A" : m.cpu().temperatureC() + "°C"))
        );
        blocks.add(Map.of(
                "type", "section",
                "text", Map.of("type", "mrkdwn", "text", cpuLine),
                "fields", cpuFields
        ));

        // Memory
        String memLine = "*Memory*\n```" + progressBar(m.memory().usedPercent()) + " " + round2(m.memory().usedPercent()) +
                "% (" + humanBytes(m.memory().usedBytes()) + " / " + humanBytes(m.memory().totalBytes()) + ")```";
        blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", memLine)));

        // Swap
        if (m.swap().totalBytes() > 0) {
            String swapLine = "*Swap*\n```" + progressBar(m.swap().usedPercent()) + " " + round2(m.swap().usedPercent()) +
                    "% (" + humanBytes(m.swap().usedBytes()) + " / " + humanBytes(m.swap().totalBytes()) + ")```";
            blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", swapLine)));
        }

        // Storage
        if (m.storage() != null && !m.storage().isEmpty()) {
            blocks.add(Map.of("type", "divider"));
            blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", "*Storage*")));
            for (StorageDTO d : m.storage()) {
                String line = "`" + safe(d.name()) + "` • " + safe(d.type()) + "\n" +
                        "```" + progressBar(d.usedPercent()) + " " + round2(d.usedPercent()) + "% (" +
                        humanBytes(d.totalBytes() - d.usableBytes()) + " / " + humanBytes(d.totalBytes()) + ")```";
                blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", line)));
            }
        }

        // Network — list semua interface
        if (m.networks() != null && !m.networks().isEmpty()) {
            blocks.add(Map.of("type", "divider"));
            blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", "*Network Interfaces*")));

            for (NetworkDTO nif : m.networks()) {
                String ipv4 = nif.ipv4() != null && !nif.ipv4().isEmpty() ? nif.ipv4() : "-";
                String ipv6 = nif.ipv6() != null && !nif.ipv6().isEmpty() ? nif.ipv6() : "-";
                String line = "`" + safe(nif.name()) + "` • " + safe(nif.mac()) + "\n" +
                        "IPv4: " + ipv4 + " | IPv6: " + ipv6 + "\n" +
                        "↓ " + humanBytes(nif.bytesRecv()) + " • ↑ " + humanBytes(nif.bytesSent());
                blocks.add(Map.of(
                        "type", "section",
                        "text", Map.of("type", "mrkdwn", "text", line)
                ));
            }
        }

        return blocks;
    }

    /* ====================== Shared helpers ====================== */

    public static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] u = {"KB", "MB", "GB", "TB", "PB"};
        int i = -1;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format("%.2f %s", v, u[i]);
    }

    public static String progressBar(double percent) {
        int filled = (int) Math.round(Math.max(0, Math.min(100, percent)) / 10.0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < filled ? "█" : "░");
        return sb.toString();
    }

    public static String humanUptime(long seconds) {
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

    public static String gaugeEmoji(double percent) {
        if (percent >= 90) return "🟥";
        if (percent >= 75) return "🟧";
        if (percent >= 50) return "🟨";
        return "🟩";
    }

    public static String round2(double v) {
        return String.format("%.2f", v);
    }

    public static String safe(String s) {
        return s == null ? "" : s;
    }
}
