package id.my.agungdh.discordbotservermonitoring.controller;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.service.DiscordService;
import id.my.agungdh.discordbotservermonitoring.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;

// 👉 tambahkan import ini
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class MainController {
    private final MetricsService metricsService;
    private final DiscordService discordService;
    @Value("${spring.application.name}")
    String name;

    @GetMapping
    public String index() {
        return name;
    }

    @GetMapping("/system")
    public MetricsDTO system() {
        return metricsService.snapshot(true);
    }

    @GetMapping("interval")
    public String interval() {
        // Contoh penggunaan parser:
        PeriodDuration pd = parseInterval("10 tahun 3 bulan 2 hari 7 jam 16 menit 32 detik");
        // misal diaplikasikan ke waktu sekarang
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime later = pd.addTo(now);
        System.out.println("Now   : " + now);
        System.out.println("Plus  : " + pd.toIsoString());
        System.out.println("Result: " + later);

        return pd.toIsoString();
    }

    // ====================== BEGIN: Parser interval ======================
    /**
     * Tipe gabungan Period (Y/M/D) + Duration (H/M/S) biar bisa dipakai seperti satu interval.
     */
    private static final class PeriodDuration {
        private final Period period;
        private final Duration duration;

        private PeriodDuration(Period period, Duration duration) {
            this.period = period;
            this.duration = duration;
        }

        public static PeriodDuration of(int years, int months, int days, int hours, int minutes, int seconds) {
            return new PeriodDuration(
                    Period.of(years, months, days),
                    Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds)
            );
        }

        public ZonedDateTime addTo(ZonedDateTime t) { return t.plus(period).plus(duration); }
        public ZonedDateTime subtractFrom(ZonedDateTime t) { return t.minus(period).minus(duration); }
        public Period period() { return period; }
        public Duration duration() { return duration; }

        /** Representasi ISO gabungan: P..Y..M..DT..H..M..S (hanya komponen non-zero yang ditampilkan). */
        public String toIsoString() {
            StringBuilder sb = new StringBuilder("P");
            if (period.getYears() != 0) sb.append(period.getYears()).append("Y");
            if (period.getMonths() != 0) sb.append(period.getMonths()).append("M");
            if (period.getDays() != 0) sb.append(period.getDays()).append("D");
            if (!duration.isZero()) {
                sb.append("T");
                long hours = duration.toHoursPart();
                int minutes = duration.toMinutesPart();
                int seconds = duration.toSecondsPart();
                if (hours != 0) sb.append(hours).append("H");
                if (minutes != 0) sb.append(minutes).append("M");
                if (seconds != 0) sb.append(seconds).append("S");
                if (sb.charAt(sb.length()-1) == 'T') sb.setLength(sb.length()-1); // kalau semua time-part zero
            }
            if (sb.length() == 1) return "PT0S"; // benar-benar nol
            return sb.toString();
        }

        @Override public String toString() { return toIsoString(); }
    }

    /**
     * Parse string interval Indonesia → gabungan Period (Y/M/D) + Duration (H/M/S)
     * Mendukung urutan bebas dan beberapa alias:
     * - tahun: "tahun","th","thn","y"
     * - bulan: "bulan","bln","mo"
     * - hari : "hari","hr","d"
     * - jam  : "jam","j","h","hour","hours"
     * - menit: "menit","min","mnt","m"   (PERHATIAN: "m" di sini dianggap menit, bukan bulan)
     * - detik: "detik","dtk","sec","s"
     *
     * Contoh valid:
     *  "10 tahun 3 bulan 2 hari 7 jam 16 menit 32 detik"
     *  "7h 30min", "2 hari", "3 bln 5 dtk"
     */
    private static PeriodDuration parseInterval(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input interval kosong");
        }
        String s = input.toLowerCase(Locale.ROOT).trim();

        // cari pola: <angka><spasi optional><unit>
        Pattern token = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");
        Matcher m = token.matcher(s);

        long years = 0, months = 0, days = 0, hours = 0, minutes = 0, seconds = 0;
        int hit = 0;

        while (m.find()) {
            long val = Long.parseLong(m.group(1));
            String unit = m.group(2);

            switch (unit) {
                // tahun
                case "tahun": case "th": case "thn": case "y": case "year": case "years":
                    years += val; hit++; break;
                // bulan
                case "bulan": case "bln": case "mo": case "month": case "months":
                    months += val; hit++; break;
                // hari
                case "hari": case "hr": case "d": case "day": case "days":
                    days += val; hit++; break;
                // jam
                case "jam": case "j": case "h": case "hour": case "hours":
                    hours += val; hit++; break;
                // menit  (catatan: 'm' dianggap menit, bukan bulan)
                case "menit": case "min": case "mnt": case "m": case "minute": case "minutes":
                    minutes += val; hit++; break;
                // detik
                case "detik": case "dtk": case "sec": case "s": case "second": case "seconds":
                    seconds += val; hit++; break;

                default:
                    throw new IllegalArgumentException("Satuan tidak dikenali: " + unit);
            }
        }

        if (hit == 0) {
            throw new IllegalArgumentException("Format interval tidak valid: " + input);
        }

        // pastikan muat di int untuk Period; Duration menerima long tapi kita pakai builder gabungan
        if (years > Integer.MAX_VALUE || months > Integer.MAX_VALUE || days > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Komponen tahun/bulan/hari terlalu besar");
        }

        return PeriodDuration.of(
                (int) years, (int) months, (int) days,
                (int) hours, (int) minutes, (int) seconds
        );
    }
    // ====================== END: Parser interval ======================
}
