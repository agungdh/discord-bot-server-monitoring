package id.my.agungdh.discordbotservermonitoring.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JapanHolidayService {

    public static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");
    // Hardcoded libur Jepang 2025 (termasuk "substitute holiday")
    private static final List<Holiday> HOLIDAYS_2025 = List.of(
            new Holiday(LocalDate.of(2025, 1, 1), "New Year's Day"),
            new Holiday(LocalDate.of(2025, 1, 13), "Coming of Age Day"),
            new Holiday(LocalDate.of(2025, 2, 11), "National Foundation Day"),
            new Holiday(LocalDate.of(2025, 2, 23), "Emperor's Birthday"),
            new Holiday(LocalDate.of(2025, 2, 24), "Substitute Holiday (Emperor's Birthday)"),
            new Holiday(LocalDate.of(2025, 3, 20), "Vernal Equinox Day"),
            new Holiday(LocalDate.of(2025, 4, 29), "Showa Day"),
            new Holiday(LocalDate.of(2025, 5, 3), "Constitution Memorial Day"),
            new Holiday(LocalDate.of(2025, 5, 4), "Greenery Day"),
            new Holiday(LocalDate.of(2025, 5, 5), "Children's Day"),
            new Holiday(LocalDate.of(2025, 5, 6), "Substitute Holiday (Greenery Day)"),
            new Holiday(LocalDate.of(2025, 7, 21), "Marine Day"),
            new Holiday(LocalDate.of(2025, 8, 11), "Mountain Day"),
            new Holiday(LocalDate.of(2025, 9, 15), "Respect for the Aged Day"),
            new Holiday(LocalDate.of(2025, 9, 23), "Autumnal Equinox Day"),
            new Holiday(LocalDate.of(2025, 10, 13), "Sports Day"),
            new Holiday(LocalDate.of(2025, 11, 3), "Culture Day"),
            new Holiday(LocalDate.of(2025, 11, 23), "Labor Thanksgiving Day"),
            new Holiday(LocalDate.of(2025, 11, 24), "Substitute Holiday (Labor Thanksgiving Day)")
    );
    private static final Map<Integer, List<Holiday>> HOLIDAYS_BY_YEAR = Map.of(
            2025, HOLIDAYS_2025.stream()
                    .sorted(Comparator.comparing(Holiday::date))
                    .collect(Collectors.toUnmodifiableList())
    );

    public Optional<Holiday> getHoliday(LocalDate date) {
        var list = HOLIDAYS_BY_YEAR.get(date.getYear());
        if (list == null) return Optional.empty();
        return list.stream().filter(h -> h.date().equals(date)).findFirst();
    }

    public List<Holiday> getHolidaysInMonth(YearMonth ym) {
        var list = HOLIDAYS_BY_YEAR.get(ym.getYear());
        if (list == null) return List.of();
        return list.stream().filter(h -> YearMonth.from(h.date()).equals(ym))
                .sorted(Comparator.comparing(Holiday::date))
                .toList();
    }

    public List<Holiday> getAllHolidays(int year) {
        return HOLIDAYS_BY_YEAR.getOrDefault(year, List.of());
    }

    public record Holiday(LocalDate date, String name) {
    }
}
