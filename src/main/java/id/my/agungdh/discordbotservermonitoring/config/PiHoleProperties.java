package id.my.agungdh.discordbotservermonitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "pihole")
public class PiHoleProperties {
    private String baseUrl;
    private String password;
    private long reloginIntervalMs = 1_500_000L; // default 25 menit

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public long getReloginIntervalMs() { return reloginIntervalMs; }
    public void setReloginIntervalMs(long reloginIntervalMs) { this.reloginIntervalMs = reloginIntervalMs; }
}
