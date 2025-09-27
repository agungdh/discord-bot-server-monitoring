package id.my.agungdh.discordbotservermonitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProps {
    public static class Node {
        private String name;
        private String url;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    private List<Node> nodes = List.of();

    // NEW: tampilkan interface virtual (veth/docker/br/lo) — default true (semua tampil)
    private boolean includeVirtualIfaces = true;

    // NEW: tampilkan filesystem “special” (tmpfs/overlay/proc/sys/run/zram) — default true (semua tampil)
    private boolean includeSpecialFilesystems = true;

    public List<Node> getNodes() { return nodes; }
    public void setNodes(List<Node> nodes) { this.nodes = nodes; }

    public boolean isIncludeVirtualIfaces() { return includeVirtualIfaces; }
    public void setIncludeVirtualIfaces(boolean includeVirtualIfaces) { this.includeVirtualIfaces = includeVirtualIfaces; }

    public boolean isIncludeSpecialFilesystems() { return includeSpecialFilesystems; }
    public void setIncludeSpecialFilesystems(boolean includeSpecialFilesystems) { this.includeSpecialFilesystems = includeSpecialFilesystems; }
}
