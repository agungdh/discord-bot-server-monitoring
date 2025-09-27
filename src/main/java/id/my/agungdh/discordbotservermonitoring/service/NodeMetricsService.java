package id.my.agungdh.discordbotservermonitoring.service;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.CpuDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MemoryDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.NetworkDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.StorageDTO;
import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.SwapDTO;
import id.my.agungdh.discordbotservermonitoring.client.NodeExporterClient;
import id.my.agungdh.discordbotservermonitoring.config.MonitoringProps;
import org.springframework.beans.factory.annotation.Value;
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
    private final long cpuSampleMillis;

    public NodeMetricsService(NodeExporterClient client,
                              MonitoringProps props,
                              @Value("${monitoring.cpuSampleMillis:300}") long cpuSampleMillis) {
        this.client = client;
        this.props = props;
        this.cpuSampleMillis = cpuSampleMillis;
    }

    /** Snapshot satu node langsung dari node_exporter (tanpa Prometheus). */
    public MetricsDTO snapshotFromUrl(String nameOrHost, String baseUrl, boolean includeNetwork) {
        // 2x scrape untuk menghitung delta CPU yang akurat
        var s1 = client.scrape(baseUrl);
        sleepSilently(cpuSampleMillis);
        var s2 = client.scrape(baseUrl);

        var idx1 = new Index(s1);
        var idx2 = new Index(s2);

        // Waktu & uptime
        double nowEpoch = idx2.firstValue("time").orElse(System.currentTimeMillis() / 1000.0);
        double boot = idx2.firstValue("node_boot_time_seconds").orElse(nowEpoch);
        long uptimeSec = Math.max(0, Math.round(nowEpoch - boot));

        // Hostname & OS
        String hostname = idx2.firstLabel("node_uname_info", "nodename").orElse(nameOrHost);
        String os = buildOs(idx2);

        // CPU via delta counter
        CpuCalc cpu = computeCpu(idx1, idx2);

        // Memory
        long memTotal = optToLong(idx2.firstValue("node_memory_MemTotal_bytes"));
        long memAvail = optToLong(idx2.firstValue("node_memory_MemAvailable_bytes"));
        long memUsed  = Math.max(0, memTotal - memAvail);
        double memPct = memTotal == 0 ? 0 : (memUsed * 100.0 / memTotal);

        // Swap
        long swapTotal = optToLong(idx2.firstValue("node_memory_SwapTotal_bytes"));
        long swapFree  = optToLong(idx2.firstValue("node_memory_SwapFree_bytes"));
        long swapUsed  = Math.max(0, swapTotal - swapFree);
        double swapPct = swapTotal == 0 ? 0 : (swapUsed * 100.0 / swapTotal);

        // Disks & Networks (pakai toggle dari props)
        List<StorageDTO> disks = buildDisks(idx2, props.isIncludeSpecialFilesystems());
        List<NetworkDTO> nets  = includeNetwork ? buildNetworks(idx2, props.isIncludeVirtualIfaces()) : List.of();

        return new MetricsDTO(
                Instant.now(),
                hostname,
                os,
                uptimeSec,
                new CpuDTO(
                        // node_exporter tidak expose "model" portable; pakai machine sebagai fallback
                        idx2.firstLabel("node_uname_info", "machine").orElse(hostname),
                        cpu.physicalCores,
                        cpu.logicalCores,
                        cpu.load1,
                        round2(cpu.totalUsagePct),
                        cpu.perCorePct,
                        cpu.temperatureC
                ),
                new MemoryDTO(memTotal, memUsed, memAvail, round2(memPct)),
                new SwapDTO(swapTotal, swapUsed, round2(swapPct)),
                disks,
                nets
        );
    }

    /** Snapshot semua node dari konfigurasi. */
    public Map<String, MetricsDTO> snapshotAll(boolean includeNetwork) {
        Map<String, MetricsDTO> out = new LinkedHashMap<>();
        for (var n : props.getNodes()) {
            String key = (n.getName() != null && !n.getName().isBlank()) ? n.getName() : n.getUrl();
            try {
                out.put(key, snapshotFromUrl(key, n.getUrl(), includeNetwork));
            } catch (Exception e) {
                out.put(key, errorPlaceholder(key));
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

    // ========= CPU calc via delta =========

    private record CpuCalc(int logicalCores, int physicalCores, double load1,
                           double totalUsagePct, List<Double> perCorePct, Double temperatureC) {}

    private CpuCalc computeCpu(Index a, Index b) {
        // load1 & suhu (opsional)
        double load1 = b.firstValue("node_load1").orElse(-1.0);
        Double temp  = b.maxOf("node_hwmon_temp_celsius")
                .orElseGet(() -> b.maxOf("node_thermal_zone_temp").orElse(null));
        if (temp != null) temp = round2(temp);

        // Delta per core
        Map<String, Double> totalDelta = new HashMap<>();
        Map<String, Double> idleDelta  = new HashMap<>();

        Set<String> cpuIds = b.distinctValues("node_cpu_seconds_total", "cpu");
        Set<String> modes  = b.distinctValues("node_cpu_seconds_total", "mode");

        for (String cpuId : cpuIds) {
            double sumTot = 0.0;
            double idle   = 0.0;
            for (String mode : modes) {
                double v2 = b.firstValue("node_cpu_seconds_total", Map.of("cpu", cpuId, "mode", mode)).orElse(Double.NaN);
                double v1 = a.firstValue("node_cpu_seconds_total", Map.of("cpu", cpuId, "mode", mode)).orElse(Double.NaN);
                if (Double.isNaN(v1) || Double.isNaN(v2)) continue;
                double d = Math.max(0.0, v2 - v1);
                sumTot += d;
                if ("idle".equals(mode)) idle = d;
            }
            totalDelta.put(cpuId, sumTot);
            idleDelta.put(cpuId, idle);
        }

        List<String> sortedCpu = totalDelta.keySet().stream()
                .sorted(Comparator.comparingInt(NodeMetricsService::parseCpuIndex))
                .collect(Collectors.toList());

        List<Double> perCore = new ArrayList<>(sortedCpu.size());
        double sumCore = 0.0;
        for (String cpu : sortedCpu) {
            double tot = totalDelta.getOrDefault(cpu, 0.0);
            double idl = idleDelta.getOrDefault(cpu, 0.0);
            double pct = (tot <= 0) ? 0.0 : (1.0 - (idl / tot)) * 100.0;
            pct = clampPct(pct);
            perCore.add(round2(pct));
            sumCore += pct;
        }

        int logical = sortedCpu.size();
        int physical = logical; // tidak portable → samakan agar field int selalu terisi
        double totalPct = (logical == 0) ? 0.0 : sumCore / logical;

        return new CpuCalc(logical, physical, load1, totalPct, perCore, temp);
    }

    // ========= Storage & Network =========

    private static List<StorageDTO> buildDisks(Index idx, boolean includeSpecialFs) {
        // Kalau includeSpecialFs = true => tampilkan SEMUA filesystem, termasuk tmpfs/overlay/proc/sys/run/zram.
        // Kalau false => pakai filter “normal” seperti versi sebelumnya.
        java.util.function.Predicate<Map<String,String>> filter = lab -> true;
        if (!includeSpecialFs) {
            filter = lab -> !lab.getOrDefault("fstype","").matches("^(tmpfs|overlay)$")
                    && !lab.getOrDefault("mountpoint","").matches("^/(proc|sys|run)($|/).*");
        }

        var sizes = idx.mapFirstValues(
                "node_filesystem_size_bytes",
                Map.of(), "device","mountpoint","fstype",
                filter
        );

        var avails = idx.mapFirstValues(
                "node_filesystem_avail_bytes",
                Map.of(), "device","mountpoint","fstype",
                filter
        );

        List<StorageDTO> out = new ArrayList<>();
        for (var e : sizes.entrySet()) {
            long total = e.getValue();
            long usable = avails.getOrDefault(e.getKey(), 0L);
            long used   = Math.max(0, total - usable);
            double pct  = total == 0 ? 0 : (used * 100.0 / total);
            String name = e.getKey().mountpoint().isBlank() ? e.getKey().device() : e.getKey().mountpoint();
            out.add(new StorageDTO(name, e.getKey().fstype(), total, usable, round2(pct)));
        }
        // Tambahan: kalau ada zram yang tidak punya size/avail lengkap di momen tertentu, dia mungkin tidak muncul.
        // Tapi umumnya node_exporter publish keduanya → aman.

        out.sort(Comparator.comparing(StorageDTO::name));
        return out;
    }

    private static List<NetworkDTO> buildNetworks(Index idx, boolean includeVirtualIfaces) {
        // Kalau includeVirtualIfaces = true => tampilkan SEMUA device (termasuk lo, veth*, docker*, br-*, dll).
        // Kalau false => hide seperti sebelumnya.
        java.util.function.Predicate<String> allow = dev -> true;
        if (!includeVirtualIfaces) {
            allow = dev -> !dev.matches("^(lo|veth.*|docker.*|br-.*)$");
        }

        var rx = idx.mapFirstValuesByDevice("node_network_receive_bytes_total", allow);
        var tx = idx.mapFirstValuesByDevice("node_network_transmit_bytes_total", allow);

        Set<String> devs = new TreeSet<>();
        devs.addAll(rx.keySet());
        devs.addAll(tx.keySet());

        List<NetworkDTO> out = new ArrayList<>();
        for (String d : devs) {
            out.add(new NetworkDTO(
                    d,
                    "", "", "", // node_exporter tidak expose MAC/IP → biarkan kosong
                    rx.getOrDefault(d, 0L),
                    tx.getOrDefault(d, 0L)
            ));
        }
        return out;
    }

    // ========= Utils =========

    private static long optToLong(Optional<Double> o) {
        return o.map(d -> (long) (double) d).orElse(0L);
    }

    private static int parseCpuIndex(String s) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return Integer.MAX_VALUE; }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private static double clampPct(double v) { return Math.max(0.0, Math.min(100.0, v)); }

    private static void sleepSilently(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static MetricsDTO errorPlaceholder(String name) {
        return new MetricsDTO(
                Instant.now(), name, "unavailable", 0L,
                new CpuDTO("", 0, 0, -1, 0, List.of(), null),
                new MemoryDTO(0,0,0,0),
                new SwapDTO(0,0,0),
                List.of(), List.of()
        );
    }

    private static String buildOs(Index idx) {
        String sys  = idx.firstLabel("node_uname_info", "sysname").orElse("");
        String rel  = idx.firstLabel("node_uname_info", "release").orElse("");
        String mach = idx.firstLabel("node_uname_info", "machine").orElse("");
        String out = (sys + " " + rel + " (" + mach + ")").trim();
        return out.isBlank() ? "node_exporter" : out;
    }

    // ========= Index untuk navigasi sample =========

    private static class Index {
        private final List<NodeExporterClient.Sample> samples;
        Index(List<NodeExporterClient.Sample> samples) { this.samples = samples; }

        Optional<Double> firstValue(String name) {
            return samples.stream()
                    .filter(s -> s.name().equals(name))
                    .map(NodeExporterClient.Sample::value)
                    .findFirst();
        }

        Optional<Double> firstValue(String name, Map<String,String> match) {
            return samples.stream()
                    .filter(s -> s.name().equals(name) && labelsMatch(s.labels(), match))
                    .map(NodeExporterClient.Sample::value)
                    .findFirst();
        }

        Optional<Double> maxOf(String name) {
            return samples.stream()
                    .filter(s -> s.name().equals(name))
                    .map(NodeExporterClient.Sample::value)
                    .max(Double::compare);
        }

        Optional<String> firstLabel(String metric, String label) {
            return samples.stream()
                    .filter(s -> s.name().equals(metric))
                    .map(s -> s.labels().get(label))
                    .filter(Objects::nonNull)
                    .findFirst();
        }

        Set<String> distinctValues(String metric, String label) {
            return samples.stream()
                    .filter(s -> s.name().equals(metric))
                    .map(s -> s.labels().getOrDefault(label, ""))
                    .filter(v -> !v.isBlank())
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        record Key(String device, String mountpoint, String fstype) {}

        Map<Key, Long> mapFirstValues(String metric, Map<String,String> mustMatch,
                                      String labelDevice, String labelMount, String labelFs,
                                      java.util.function.Predicate<Map<String,String>> labelFilter) {
            Map<Key, Long> out = new HashMap<>();
            for (var s : samples) {
                if (!s.name().equals(metric)) continue;
                if (!labelsMatch(s.labels(), mustMatch)) continue;
                if (!labelFilter.test(s.labels())) continue;
                Key k = new Key(
                        s.labels().getOrDefault(labelDevice, ""),
                        s.labels().getOrDefault(labelMount, ""),
                        s.labels().getOrDefault(labelFs, "")
                );
                // pakai nilai pertama yang terlihat
                out.putIfAbsent(k, (long) (double) s.value());
            }
            return out;
        }

        Map<String, Long> mapFirstValuesByDevice(String metric, java.util.function.Predicate<String> allowDevice) {
            Map<String, Long> out = new HashMap<>();
            for (var s : samples) {
                if (!s.name().equals(metric)) continue;
                String dev = s.labels().getOrDefault("device", "");
                if (!allowDevice.test(dev)) continue;
                out.putIfAbsent(dev, (long) (double) s.value());
            }
            return out;
        }

        private static boolean labelsMatch(Map<String,String> have, Map<String,String> need) {
            for (var e : need.entrySet()) {
                if (!e.getValue().equals(have.getOrDefault(e.getKey(), ""))) return false;
            }
            return true;
        }
    }
}
