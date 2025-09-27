// client/NodeExporterClient.java
package id.my.agungdh.discordbotservermonitoring.client;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NodeExporterClient {

    // ---- parser Prometheus text exposition (minimal & cepat) ----
    private static final Pattern LINE = Pattern.compile(
            "^([a-zA-Z_:][a-zA-Z0-9_:]*)(\\{([^}]*)})?\\s+([-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?)"
    );
    private final RestClient http;

    public NodeExporterClient(RestClient.Builder builder) {
        this.http = builder.build();
    }

    private static List<Sample> parseTextFormat(String text) {
        List<Sample> out = new ArrayList<>(2048);
        for (String raw : text.split("\\r?\\n")) {
            if (raw.isEmpty() || raw.charAt(0) == '#') continue;
            Matcher m = LINE.matcher(raw);
            if (!m.find()) continue;
            String name = m.group(1);
            String lbls = m.group(3);
            String valStr = m.group(4);
            double value;
            try {
                value = Double.parseDouble(valStr);
            } catch (Exception e) {
                continue;
            }
            Map<String, String> labels = (lbls == null) ? Map.of() : parseLabels(lbls);
            out.add(new Sample(name, labels, value));
        }
        return out;
    }

    private static Map<String, String> parseLabels(String s) {
        Map<String, String> map = new LinkedHashMap<>();
        int i = 0, n = s.length();
        while (i < n) {
            int startKey = i;
            while (i < n && s.charAt(i) != '=') i++;
            if (i >= n) break;
            String key = s.substring(startKey, i).trim();
            i++; // '='
            if (i >= n || s.charAt(i) != '"') break;
            i++; // opening "
            StringBuilder val = new StringBuilder();
            while (i < n) {
                char c = s.charAt(i++);
                if (c == '\\' && i < n) {
                    char esc = s.charAt(i++);
                    val.append(switch (esc) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case '\\' -> '\\';
                        case '"' -> '"';
                        default -> esc;
                    });
                } else if (c == '"') break;
                else val.append(c);
            }
            map.put(key, val.toString());
            while (i < n && s.charAt(i) == ' ') i++;
            if (i < n && s.charAt(i) == ',') i++;
            while (i < n && s.charAt(i) == ' ') i++;
        }
        return map;
    }

    public List<Sample> scrape(String baseUrl) {
        String url = baseUrl.endsWith("/") ? baseUrl + "metrics" : baseUrl + "/metrics";
        ResponseEntity<String> resp = http.get().uri(url).retrieve().toEntity(String.class);
        String body = resp.getBody();
        if (body == null || body.isBlank()) return List.of();
        return parseTextFormat(body);
    }

    public record Sample(String name, Map<String, String> labels, double value) {
    }
}
