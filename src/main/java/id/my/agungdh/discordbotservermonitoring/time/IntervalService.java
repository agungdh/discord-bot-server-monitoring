package id.my.agungdh.discordbotservermonitoring.time;

import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IntervalService {

    // pola: <angka><spasi optional><unit>
    private static final Pattern TOKEN = Pattern.compile("(\\d++)\\s*+([a-zA-Z]++)");

    /**
     * Parse string interval Indonesia → PeriodDuration (Y/M/D + H/M/S)
     * Alias:
     * tahun: tahun, th, thn, y, year, years
     * bulan: bulan, bln, mo, month, months
     * hari : hari, hr, d, day, days
     * jam  : jam, j, h, hour, hours
     * menit: menit, min, mnt, m, minute, minutes   (CATATAN: 'm' = menit)
     * detik: detik, dtk, sec, s, second, seconds
     */
    public PeriodDuration parseInterval(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input interval kosong");
        }
        String s = input.toLowerCase(Locale.ROOT).trim();

        Matcher m = TOKEN.matcher(s);

        long years = 0, months = 0, days = 0, hours = 0, minutes = 0, seconds = 0;
        int hit = 0;

        while (m.find()) {
            long val = Long.parseLong(m.group(1));
            String unit = m.group(2);

            switch (unit) {
                case "tahun":
                case "th":
                case "thn":
                case "y":
                case "year":
                case "years":
                    years += val;
                    hit++;
                    break;
                case "bulan":
                case "bln":
                case "mo":
                case "month":
                case "months":
                    months += val;
                    hit++;
                    break;
                case "hari":
                case "hr":
                case "d":
                case "day":
                case "days":
                    days += val;
                    hit++;
                    break;
                case "jam":
                case "j":
                case "h":
                case "hour":
                case "hours":
                    hours += val;
                    hit++;
                    break;
                case "menit":
                case "min":
                case "mnt":
                case "m":
                case "minute":
                case "minutes":
                    minutes += val;
                    hit++;
                    break;
                case "detik":
                case "dtk":
                case "sec":
                case "s":
                case "second":
                case "seconds":
                    seconds += val;
                    hit++;
                    break;
                default:
                    throw new IllegalArgumentException("Satuan tidak dikenali: " + unit);
            }
        }

        if (hit == 0) {
            throw new IllegalArgumentException("Format interval tidak valid: " + input);
        }
        if (years > Integer.MAX_VALUE || months > Integer.MAX_VALUE || days > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Komponen tahun/bulan/hari terlalu besar");
        }

        return PeriodDuration.of((int) years, (int) months, (int) days, (int) hours, (int) minutes, (int) seconds);
    }

    /**
     * Utility kecil: kurangi dari 'now' (zona waktu sistem).
     */
    public ZonedDateTime minusFromNow(PeriodDuration pd) {
        return pd.subtractFrom(ZonedDateTime.now());
    }

    /**
     * Utility kecil: tambah ke 'now' (kalau butuh kedepan).
     */
    public ZonedDateTime plusFromNow(PeriodDuration pd) {
        return pd.addTo(ZonedDateTime.now());
    }
}
