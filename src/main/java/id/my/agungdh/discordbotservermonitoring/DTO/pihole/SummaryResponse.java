package id.my.agungdh.discordbotservermonitoring.DTO.pihole;

import java.util.Map;

public record SummaryResponse(
        Queries queries,
        Clients clients,
        Gravity gravity,
        double took
) {
    public record Queries(
            long total,
            long blocked,
            double percent_blocked,
            long unique_domains,
            long forwarded,
            long cached,
            double frequency,
            Map<String, Long> types,
            Map<String, Long> status,
            Map<String, Long> replies
    ) {
    }

    public record Clients(
            int active,
            int total
    ) {
    }

    public record Gravity(
            long domains_being_blocked,
            long last_update
    ) {
    }
}
