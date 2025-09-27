// client/NodeExporterClient.java
package id.my.agungdh.discordbotservermonitoring.client;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NodeExporterClient {

    private final RestClient http;

    public NodeExporterClient(RestClient.Builder builder) {
        this.http = builder.build();
    }

    public List<Sample> scrape(String baseUrl) {
        String url = baseUrl.endsWith("/") ? baseUrl + "metrics" : baseUrl + "/metrics";
        ResponseEntity<String> resp = http.get().uri(url).retrieve().toEntity(String.class);
        String body = resp.getBody();
        if (body == null || body.isBlank()) return List.of();
        return parseTextFormat(body);
    }

    public record Sample(String name, Map<String,String> labels, double value) {}

    // ---- Minimal parser untuk Prometheus text exposition ----
    private static final Pattern LINE = Pattern.compile(
            "^([a-zA-Z_:][a-zA-Z0-9_:]*)(\\{([^}]*)})?\\s+([-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?)" // metric{labels} value
    );

    private static List<Sample> parseTextFormat(String text) {
        List<Sample> out = new ArrayList<>(2048);
        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            if (raw.isEmpty() || raw.charAt(0) == '#') continue; // HELP/TYPE/comments
            Matcher m = LINE.matcher(raw);
            if (!m.find()) continue;
            String name = m.group(1);
            String lbls = m.group(3);
            String valStr = m.group(4);
            double value;
            try { value = Double.parseDouble(valStr); } catch (Exception e) { continue; }
            Map<String,String> labels = lbls == null ? Map.of() : parseLabels(lbls);
            out.add(new Sample(name, labels, value));
        }
        return out;
    }

    private static Map<String,String> parseLabels(String s) {
        // key="value",key2="va\"lue2"
        Map<String,String> map = new LinkedHashMap<>();
        int i = 0, n = s.length();
        while (i < n) {
            // parse key
            int startKey = i;
            while (i < n && s.charAt(i) != '=') i++;
            if (i >= n) break;
            String key = s.substring(startKey, i).trim();
            i++; // skip =
            if (i >= n || s.charAt(i) != '"') break;
            i++; // skip opening "
            StringBuilder val = new StringBuilder();
            while (i < n) {
                char c = s.charAt(i++);
                if (c == '\\' && i < n) {
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case 'n' -> val.append('\n');
                        case 't' -> val.append('\t');
                        case '\\' -> val.append('\\');
                        case '"' -> val.append('"');
                        default -> val.append(esc);
                    }
                } else if (c == '"') {
                    break;
                } else {
                    val.append(c);
                }
            }
            map.put(key, val.toString());
            // skip comma
            while (i < n && s.charAt(i) == ' ') i++;
            if (i < n && s.charAt(i) == ',') i++;
            while (i < n && s.charAt(i) == ' ') i++;
        }
        return map;
    }
}
