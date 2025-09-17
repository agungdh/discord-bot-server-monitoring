package id.my.agungdh.discordbotservermonitoring.util;


import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;


public final class MessageUtils {
    private MessageUtils() {
    }


    public static String codeBlock(String s) {
        return "```" + s + "```";
    }


    public static String safe(String s) {
        return s == null ? "" : s;
    }


    public static List<String> chunkString(String s, int maxLen) {
        List<String> parts = new ArrayList<>();
        String remaining = s;
        while (remaining.length() > maxLen) {
            int cut = Math.min(maxLen, remaining.length());
            int nl = remaining.lastIndexOf('\n', maxLen);
            if (nl > 0) cut = nl;
            parts.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut);
        }
        if (!remaining.isBlank()) parts.add(remaining);
        return parts;
    }


    public static List<String> paginateLabeled(String title, String content, int pageLen) {
        List<String> chunks = chunkString(content, pageLen);
        List<String> labeled = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            labeled.add("**" + title + " (Page " + (i + 1) + "/" + chunks.size() + ")**\n\n" + chunks.get(i));
        }
        return labeled;
    }


    public static List<String> toCodeBlocks(List<String> rawParts) {
        List<String> blocks = new ArrayList<>(rawParts.size());
        for (String p : rawParts) blocks.add(codeBlock(p));
        return blocks;
    }


    public static CompletableFuture<Void> sendSequentially(InteractionHook hook, List<String> messages) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String msg : messages) {
            chain = chain.thenCompose(ignored -> toCF(hook.sendMessage(msg)).thenApply(x -> null));
        }
        return chain;
    }


    public static <T> CompletableFuture<T> toCF(RestAction<T> ra) {
        var cf = new CompletableFuture<T>();
        ra.queue(cf::complete, cf::completeExceptionally);
        return cf;
    }


    public static String humanBytes(long bytes) {
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


    public static String progressBar(double percent) {
        int filled = (int) Math.round(Math.max(0, Math.min(100, percent)) / 10.0);
        StringBuilder sb = new StringBuilder(10);
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
        return String.format(Locale.US, "%.2f", v);
    }
}