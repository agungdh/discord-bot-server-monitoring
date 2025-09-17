package id.my.agungdh.discordbotservermonitoring.commands;

import id.my.agungdh.discordbotservermonitoring.client.PrometheusClient;
import id.my.agungdh.discordbotservermonitoring.service.ErrorMinutesService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ErrorsCommand implements SlashCommand {

    private final ErrorMinutesService svc;

    public ErrorsCommand(ErrorMinutesService errorMinutesService) {
        this.svc = errorMinutesService;
    }

    @Override
    public String name() {
        return "errors";
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        // PUBLIC reply (bisa dilihat semua orang)
        event.deferReply(false).queue();

        var today = svc.errorMinutesToday();
        var yday  = svc.errorMinutesYesterday();
        var d2ago = svc.errorMinutesTwoDaysAgo();
        var w1    = svc.errorMinutesLastWeekUntilNow();
        var w2    = svc.errorMinutesLast2WeeksUntilNow();

        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();
        Instant startToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant startYday  = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant();
        Instant startD2    = LocalDate.now(zone).minusDays(2).atStartOfDay(zone).toInstant();
        Instant startW1    = LocalDate.now(zone).minusDays(7).atStartOfDay(zone).toInstant();
        Instant startW2    = LocalDate.now(zone).minusDays(14).atStartOfDay(zone).toInstant();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("📊 Error Minutes (≥5 gagal/menit, guarded)");
        eb.setDescription("Zona waktu: **" + zone + "**");

        eb.addField("Hari ini",        summarize(startToday, now, today), false);
        eb.addField("Kemarin",         summarize(startYday,  startToday, yday), false);
        eb.addField("Kemarin lusa",    summarize(startD2,    startYday,  d2ago), false);
        eb.addField("1 minggu terakhir", summarize(startW1,  now, w1), false);
        eb.addField("2 minggu terakhir", summarize(startW2,  now, w2), false);

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }

    private String summarize(Instant start, Instant end, List<PrometheusClient.ResultPoint> points) {
        var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
        long total = Math.round(points.stream().mapToDouble(PrometheusClient.ResultPoint::value).sum());
        String header = "**Range:** " + fmt.format(start) + " → " + fmt.format(end)
                + "\n**Total:** " + total + " menit";

        if (points.isEmpty()) return header + "\n*(no data)*";

        String body = points.stream()
                .sorted(Comparator.comparingDouble(PrometheusClient.ResultPoint::value).reversed())
                .map(p -> {
                    String alias = (p.alias() == null || p.alias().isBlank()) ? "-" : p.alias();
                    long mins = Math.round(p.value());
                    return "• **" + alias + "** (`" + p.instance() + "`): " + mins + " m";
                })
                .collect(Collectors.joining("\n"));
        return header + "\n" + body;
    }
}
