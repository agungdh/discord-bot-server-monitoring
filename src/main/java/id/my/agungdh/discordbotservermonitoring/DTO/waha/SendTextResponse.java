// src/main/java/id/my/agungdh/discordbotservermonitoring/DTO/waha/SendTextResponse.java
package id.my.agungdh.discordbotservermonitoring.DTO.waha;

public record SendTextResponse(
        boolean success,
        String messageId,
        String error
) {}
