package id.my.agungdh.discordbotservermonitoring.commands;

import id.my.agungdh.discordbotservermonitoring.client.PrometheusClient;
import id.my.agungdh.discordbotservermonitoring.service.ErrorMinutesService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class ErrorsCommand implements SlashCommand {

    private final ErrorMinutesService svc;
    private final Executor executor; // pakai thread pool dari Spring

    public ErrorsCommand(ErrorMinutesService errorMinutesService,
                         @Qualifier("commandExecutor") Executor executor) {
        this.svc = errorMinutesService;
        this.executor = executor;
    }

    @Override
    public String name() {
        return "errors";
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        // PUBLIC reply (bisa dilihat semua orang)
        event.deferReply(false).queue(hook -> {

            ZoneId zone = ZoneId.systemDefault();
            Instant now = Instant.now();
            Instant startToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
            Instant startYday  = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant();
            Instant startD2    = LocalDate.now(zone).minusDays(2).atStartOfDay(zone).toInstant();
            Instant startW1    = LocalDate.now(zone).minusDays(7).atStartOfDay(zone).toInstant();
            Instant startW2    = LocalDate.now(zone).minusDays(14).atStartOfDay(zone).toInstant();

            // ==== Jalankan SEMUA pemanggilan service secara paralel TANPA ubah service ====
            CompletableFuture<List<PrometheusClient.ResultPoint>> h1F = supplyAsync(() -> svc.errorMinutesLastHours(1));
            CompletableFuture<List<PrometheusClient.ResultPoint>> h2F = supplyAsync(() -> svc.errorMinutesLastHours(2));
            CompletableFuture<List<PrometheusClient.ResultPoint>> h3F = supplyAsync(() -> svc.errorMinutesLastHours(3));
            CompletableFuture<List<PrometheusClient.ResultPoint>> h6F = supplyAsync(() -> svc.errorMinutesLastHours(6));

            CompletableFuture<List<PrometheusClient.ResultPoint>> todayF = supplyAsync(svc::errorMinutesToday);
            CompletableFuture<List<PrometheusClient.ResultPoint>> ydayF  = supplyAsync(svc::errorMinutesYesterday);
            CompletableFuture<List<PrometheusClient.ResultPoint>> d2F    = supplyAsync(svc::errorMinutesTwoDaysAgo);
            CompletableFuture<List<PrometheusClient.ResultPoint>> w1F    = supplyAsync(svc::errorMinutesLastWeekUntilNow);
            CompletableFuture<List<PrometheusClient.ResultPoint>> w2F    = supplyAsync(svc::errorMinutesLast2WeeksUntilNow);

            // Total unik menit down per range — juga paralel (tetap panggil method sync di service)
            CompletableFuture<Long> uH1    = supplyAsync(() -> svc.errorMinutesAnyDown(now.minus(Duration.ofHours(1)), now));
            CompletableFuture<Long> uH2    = supplyAsync(() -> svc.errorMinutesAnyDown(now.minus(Duration.ofHours(2)), now));
            CompletableFuture<Long> uH3    = supplyAsync(() -> svc.errorMinutesAnyDown(now.minus(Duration.ofHours(3)), now));
            CompletableFuture<Long> uH6    = supplyAsync(() -> svc.errorMinutesAnyDown(now.minus(Duration.ofHours(6)), now));
            CompletableFuture<Long> uToday = supplyAsync(() -> svc.errorMinutesAnyDown(startToday, now));
            CompletableFuture<Long> uYday  = supplyAsync(() -> svc.errorMinutesAnyDown(startYday, startToday));
            CompletableFuture<Long> uD2    = supplyAsync(() -> svc.errorMinutesAnyDown(startD2, startYday));
            CompletableFuture<Long> uW1    = supplyAsync(() -> svc.errorMinutesAnyDown(startW1, now));
            CompletableFuture<Long> uW2    = supplyAsync(() -> svc.errorMinutesAnyDown(startW2, now));

            CompletableFuture.allOf(
                            h1F, h2F, h3F, h6F, todayF, ydayF, d2F, w1F, w2F,
                            uH1, uH2, uH3, uH6, uToday, uYday, uD2, uW1, uW2
                    )
                    .orTimeout(13, TimeUnit.MINUTES) // timeout global opsional
                    .whenComplete((ignored, err) -> {
                        if (err != null) {
                            hook.editOriginal("⚠️ Gagal ambil data error minutes: " + err.getMessage()).queue();
                            return;
                        }

                        // Kumpulkan hasil (aman karena allOf sudah selesai)
                        var h1    = h1F.join();
                        var h2    = h2F.join();
                        var h3    = h3F.join();
                        var h6    = h6F.join();
                        var today = todayF.join();
                        var yday  = ydayF.join();
                        var d2    = d2F.join();
                        var w1    = w1F.join();
                        var w2    = w2F.join();

                        long tuH1    = uH1.join();
                        long tuH2    = uH2.join();
                        long tuH3    = uH3.join();
                        long tuH6    = uH6.join();
                        long tuToday = uToday.join();
                        long tuYday  = uYday.join();
                        long tuD2    = uD2.join();
                        long tuW1    = uW1.join();
                        long tuW2    = uW2.join();

                        // Build embed
                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setTitle("📊 Error Minutes (≥5 gagal/menit, guarded)");
                        eb.setDescription("Zona waktu: **" + zone + "**");

                        eb.addField("1 jam terakhir",
                                summarize(now.minus(Duration.ofHours(1)), now, h1, tuH1), false);
                        eb.addField("2 jam terakhir",
                                summarize(now.minus(Duration.ofHours(2)), now, h2, tuH2), false);
                        eb.addField("3 jam terakhir",
                                summarize(now.minus(Duration.ofHours(3)), now, h3, tuH3), false);
                        eb.addField("6 jam terakhir",
                                summarize(now.minus(Duration.ofHours(6)), now, h6, tuH6), false);

                        eb.addField("Hari ini",
                                summarize(startToday, now, today, tuToday), false);
                        eb.addField("Kemarin",
                                summarize(startYday, startToday, yday, tuYday), false);
                        eb.addField("Kemarin lusa",
                                summarize(startD2, startYday, d2, tuD2), false);
                        eb.addField("1 minggu terakhir",
                                summarize(startW1, now, w1, tuW1), false);
                        eb.addField("2 minggu terakhir",
                                summarize(startW2, now, w2, tuW2), false);

                        hook.editOriginalEmbeds(eb.build()).queue();
                    });
        });
    }

    /** Helper untuk submit tugas sync ke executor tanpa ubah service. */
    private <T> CompletableFuture<T> supplyAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try { return task.call(); }
            catch (Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    private String summarize(Instant start, Instant end,
                             List<PrometheusClient.ResultPoint> points,
                             long uniqueTotal) {
        var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

        String header = "**Range:** " + fmt.format(start) + " → " + fmt.format(end)
                + "\n**Total:** " + formatMinutes(uniqueTotal);

        if (points == null || points.isEmpty()) return header + "\n*(no data)*";

        String body = points.stream()
                .sorted(Comparator.comparingDouble(PrometheusClient.ResultPoint::value).reversed())
                .map(p -> {
                    String alias = (p.alias() == null || p.alias().isBlank()) ? "-" : p.alias();
                    long mins = Math.round(p.value());
                    return "• **" + alias + "** (`" + p.instance() + "`): " + formatMinutes(mins);
                })
                .collect(Collectors.joining("\n"));
        return header + "\n" + body;
    }

    private String formatMinutes(long minutes) {
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        long mins = minutes % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" hari ");
        if (hours > 0) sb.append(hours).append(" jam ");
        if (mins > 0 || sb.isEmpty()) sb.append(mins).append(" menit");
        return sb.toString().trim();
    }
}
