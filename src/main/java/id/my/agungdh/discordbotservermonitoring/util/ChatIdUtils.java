// src/main/java/id/my/agungdh/discordbotservermonitoring/util/ChatIdUtils.java
package id.my.agungdh.discordbotservermonitoring.util;

public final class ChatIdUtils {
    private ChatIdUtils() {}

    public static String toChatId(String msisdn) {
        String digits = msisdn.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) throw new IllegalArgumentException("Nomor WhatsApp tidak valid");
        return digits + "@c.us";
    }
}
