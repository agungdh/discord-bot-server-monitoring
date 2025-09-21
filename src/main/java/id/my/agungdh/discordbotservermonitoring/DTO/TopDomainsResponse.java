package id.my.agungdh.discordbotservermonitoring.DTO;

import java.util.List;

public record TopDomainsResponse(
        List<DomainCount> domains,
        long total_queries,
        long blocked_queries,
        double took
) {
    public record DomainCount(String domain, long count) {}
}
