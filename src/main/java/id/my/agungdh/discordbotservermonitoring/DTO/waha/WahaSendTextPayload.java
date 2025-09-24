// src/main/java/id/my/agungdh/discordbotservermonitoring/DTO/waha/WahaSendTextPayload.java
package id.my.agungdh.discordbotservermonitoring.DTO.waha;

public record WahaSendTextPayload(
        String session,
        String chatId,
        String text
) {
}
