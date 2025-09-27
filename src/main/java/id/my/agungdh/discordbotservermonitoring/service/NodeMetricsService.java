// service/NodeMetricsService.java
package id.my.agungdh.discordbotservermonitoring.service;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.*;
import id.my.agungdh.discordbotservermonitoring.client.NodeExporterClient;
import id.my.agungdh.discordbotservermonitoring.config.MonitoringProps;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class NodeMetricsService {

    private final NodeExporterClient client;
    private final MonitoringProps props;

    public NodeMetricsService(NodeExporterClient client, MonitoringProps props) {
        this.client = client;
        this.props = props;
    }

    // === Single node ===
    public MetricsDTO snapshotFromUrl(String nameOrHost, String baseUrl, boolean includeNetwork) {
        List<NodeExporterClient.Sample> samples = client.scrape(baseUrl);
        Index idx = new Index(samples);

        // time & uptime
        double nowEpoch = idx.firstValue("time").orElseGet(() -> (System.currentTimeMillis()/1000.0));
        double boot = idx.firstValue("node_boot_time_seconds").orElse(nowEpoch);
        long uptimeSec = (long) Math.max(0, Math.round(nowEpoch - boot));

        // hostname / os
        String hostname = idx.firstLabel("node_uname_info", "nodename").orElse(nameOrHost);
        String os = buildOs(idx);

        // CPU
        int logicalCores = idx.distinctLabelCount("node_cpu_seconds_total", "cpu",
                Map.of("mode", "idle"));
        double load1 = idx.firstValue("node_load1").orElse(-1.0);
        double cpuPct = logicalCores > 0 && load1 >= 0 ? Math.min(100.0, (load1 / logicalCores) * 100.0) : 0.0;

        List<Double> perCorePct = new ArrayList<>();
        if (logicalCores > 0 && load1 >= 0) {
            // distribusikan rata (tanpa rate per core); lebih baik daripada kosong
            for (int i = 0; i < logicalCores; i++) perCorePct.add(round2(cpuPct));
        }

        Double temp = idx.maxOf("node_hwmon_temp_celsius").orElseGet(() ->
                idx.maxOf("node_thermal_zone_temp").orElse(null));
        CpuDTO cpuDTO = new CpuDTO(
                hostname,   // node_exporter tidak expose 'model' portable
                null,       // physical cores tidak portable
                logicalCores,
                load1,
                round2(cpuPct),
                perCorePct,
                temp == null ? null : round2(temp)
        );

        // Memory
        long memTotal = (long) idx.firstValue("node_memory_MemTotal_bytes").orElse(0.0);
        long memAvail = (long) idx.firstValue("node_memory_MemAvailable_bytes").orElse(0.0);
        long memUsed = Math.max(0, memTotal - memAvail);
        double memPct = memTotal == 0 ? 0 : (memUsed * 100.0 / memTotal);
        MemoryDTO memoryDTO = new MemoryDTO(memTotal, memUsed, memAvail, round2(memPct));

        // Swap
        long swapTotal = (long) idx.firstValue("node_memory_SwapTotal_bytes").orElse(0.0);
        long swapFree  = (long) idx.firstValue("node_memory_SwapFree_bytes").orElse(0.0);
        long swapUsed  = Math.max(0, swapTotal - swapFree);
        double swapPct = swapTotal == 0 ? 0 : (swapUsed * 100.0 / swapTotal);
        SwapDTO swapDTO = new SwapDTO(swapTotal, swapUsed, round2(swapPct));

        // Storage
        List<StorageDTO> disks = buildDisks(idx);

        // Network
        List<NetworkDTO> nets = includeNetwork ? buildNetworks(idx) : List.of();

        return new MetricsDTO(
                Instant.now(),
                hostname,
                os,
                uptimeSec,
                cpuDTO,
                memoryDTO,
                swapDTO,
                disks,
                nets
        );
    }

    // === Multi-node ===
    public Map<String, MetricsDTO> snapshotAll(boolean includeNetwork) {
        Map<String, MetricsDTO> out = new LinkedHashMap<>();
        for (var n : props.getNodes()) {
            String key = Optional.ofNullable(n.getName()).orElse(n.getUrl());
            try {
                out.put(key, snapshotFromUrl(key, n.getUrl(), includeNetwork));
            } catch (Exception e) {
                // Masukkan placeholder jika gagal agar tetap terlihat di output
                out.put(key, errorPlaceholder(key, e));
            }
        }
        return out;
    }

    @Async("commandExecutor")
    public CompletableFuture<Map<String, MetricsDTO>> snapshotAllAsync(boolean includeNetwork) {
        try {
            return CompletableFuture.completedFuture(snapshotAll(includeNetwork));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // ===== Helpers =====

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private static MetricsDTO errorPlaceholder(String name, Exception e) {
        return new MetricsDTO(
                Instant.now(), name, "unavailable", 0L,
                new CpuDTO("", null, null, -1, 0, List.of(), null),
                new MemoryDTO(0,0,0,0),
                new SwapDTO(0,0,0),
                List.of(),
                List.of()
        );
    }

    private static String buildOs(Index idx) {
        String sys  = idx.firstLabel("node_uname_info", "sysname").orElse("");
        String rel  = idx.firstLabel("node_uname_info", "release").orElse("");
        String mach = idx.firstLabel("node_uname_info", "machine").orElse("");
        String out = (sys + " " + rel + " (" + mach + ")").trim();
        return out.isBlank() ? "node_exporter" : out;
    }

    private static List<StorageDTO> buildDisks(Index idx) {
        // filter tmpfs/overlay/proc/sys/run
        Map<Key, Long> size = idx.mapFirstValues("node_filesystem_size_bytes",
                Map.of(), "device","mountpoint","fstype",
                lab -> !lab.getOrDefault("fstype","").matches("^(tmpfs|overlay)$") &&
                        !lab.getOrDefault("mountpoint","").matches("^/(proc|sys|run)($|/).*"));

        Map<Key, Long> avail = idx.mapFirstValues("node_filesystem_avail_bytes",
                Map.of(), "device","mountpoint","fstype",
                lab -> !lab.getOrDefault("fstype","").matches("^(tmpfs|overlay)$") &&
                        !lab.getOrDefault("mountpoint","").matches("^/(proc|sys|run)($|/).*"));

        List<StorageDTO> out = new ArrayList<>();
        for (var e : size.entrySet()) {
            long sz = e.getValue();
            long av = avail.getOrDefault(e.getKey(), 0L);
            long used = Math.max(0, sz - av);
            double pct = sz == 0 ? 0 : (used * 100.0 / sz);
            String name = e.getKey().mountpoint().isBlank() ? e.getKey().device() : e.getKey().mountpoint();
            out.add(new StorageDTO(name, e.getKey().fstype(), sz, av, round2(pct)));
        }
        out.sort(Comparator.comparing(StorageDTO::name));
        return out;
    }

    private static List<NetworkDTO> buildNetworks(Index idx) {
        // totals sejak boot (bukan rate)
        Map<String, Long> rx = idx.mapFirstValuesByDevice("node_network_receive_bytes_total",
                dev -> !dev.matches("^(lo|veth.*|docker.*|br-.*)$"));
        Map<String, Long> tx = idx.mapFirstValuesByDevice("node_network_transmit_bytes_total",
                dev -> !dev.matches("^(lo|veth.*|docker.*|br-.*)$"));

        Set<String> devs = new TreeSet<>();
        devs.addAll(rx.keySet());
        devs.addAll(tx.keySet());
        List<NetworkDTO> out = new ArrayList<>();
        for (String d : devs) {
            out.add(new NetworkDTO(d, "", "", "", rx.getOrDefault(d,0L), tx.getOrDefault(d,0L)));
        }
        return out;
    }

    // ===== Index util untuk cepet filter by metric/labels =====
    private static class Index {
        private final List<NodeExporterClient.Sample> samples;

        Index(List<NodeExporterClient.Sample> samples) {
            this.samples = samples;
        }

        Optional<Double> firstValue(String name) {
            return samples.stream().filter(s -> s.name().equals(name)).map(NodeExporterClient.Sample::value).findFirst();
        }

        Optional<Double> maxOf(String name) {
            return samples.stream().filter(s -> s.name().equals(name)).map(NodeExporterClient.Sample::value).max(Double::compare);
        }

        Optional<String> firstLabel(String metric, String label) {
            return samples.stream()
                    .filter(s -> s.name().equals(metric))
                    .map(s -> s.labels().get(label))
                    .filter(Objects::nonNull)
                    .findFirst();
        }

        int distinctLabelCount(String metric, String label, Map<String,String> mustMatch) {
            return (int) samples.stream()
                    .filter(s -> s.name().equals(metric) && mustMatch.entrySet().stream()
                            .allMatch(e -> e.getValue().equals(s.labels().getOrDefault(e.getKey(),""))))
                    .map(s -> s.labels().getOrDefault(label,""))
                    .distinct()
                    .count();
        }

        record Key(String device, String mountpoint, String fstype) {}

        Map<Key, Long> mapFirstValues(String metric,
                                      Map<String,String> mustMatch,
                                      String labelDevice, String labelMount, String labelFs,
                                      java.util.function.Predicate<Map<String,String>> labelFilter) {
            Map<Key, Long> out = new HashMap<>();
            for (var s : samples) {
                if (!s.name().equals(metric)) continue;
                if (!mustMatch.entrySet().stream()
                        .allMatch(e -> e.getValue().equals(s.labels().getOrDefault(e.getKey(),"")))) continue;
                if (!labelFilter.test(s.labels())) continue;
                Key k = new Key(
                        s.labels().getOrDefault(labelDevice,""),
                        s.labels().getOrDefault(labelMount,""),
                        s.labels().getOrDefault(labelFs,"")
                );
                out.putIfAbsent(k, (long) s.value()); // first seen per (device,mount,fstype)
            }
            return out;
        }

        Map<String, Long> mapFirstValuesByDevice(String metric,
                                                 java.util.function.Predicate<String> allowDevice) {
            Map<String, Long> out = new HashMap<>();
            for (var s : samples) {
                if (!s.name().equals(metric)) continue;
                String dev = s.labels().getOrDefault("device","");
                if (!allowDevice.test(dev)) continue;
                out.putIfAbsent(dev, (long) s.value());
            }
            return out;
        }
    }
}
