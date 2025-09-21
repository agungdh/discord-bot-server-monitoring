package id.my.agungdh.discordbotservermonitoring.DTO;

public record SessionWrapper(Session session, double took) {
    public record Session(
            boolean valid,
            boolean totp,
            String sid,
            String csrf,
            long validity,
            String message
    ) {
    }
}
