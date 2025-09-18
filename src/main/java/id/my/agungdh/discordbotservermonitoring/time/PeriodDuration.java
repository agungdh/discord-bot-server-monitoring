package id.my.agungdh.discordbotservermonitoring.time;

import java.time.Duration;
import java.time.Period;
import java.time.ZonedDateTime;

public final class PeriodDuration {
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

    public Period period() {
        return period;
    }

    public Duration duration() {
        return duration;
    }

    public ZonedDateTime addTo(ZonedDateTime t) {
        return t.plus(period).plus(duration);
    }

    public ZonedDateTime subtractFrom(ZonedDateTime t) {
        return t.minus(period).minus(duration);
    }

    /**
     * Representasi ISO gabungan: P..Y..M..DT..H..M..S (hanya komponen non-zero).
     */
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
            if (sb.charAt(sb.length() - 1) == 'T') sb.setLength(sb.length() - 1);
        }
        if (sb.length() == 1) return "PT0S";
        return sb.toString();
    }

    @Override
    public String toString() {
        return toIsoString();
    }
}
