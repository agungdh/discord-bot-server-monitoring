package id.my.agungdh.discordbotservermonitoring.DTO.pihole;

import java.util.List;

public record BlockListResponse(
        List<BlockList> lists,
        double took
) {
    public record BlockList(
            String address,
            String comment,
            List<Integer> groups,
            boolean enabled,
            int id,
            long date_added,
            long date_modified,
            String type,
            long date_updated,
            long number,
            int invalid_domains,
            int abp_entries,
            int status
    ) {
    }
}
