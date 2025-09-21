package id.my.agungdh.discordbotservermonitoring.service;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.NetworkIF;
import oshi.hardware.Sensors;
import oshi.software.os.OperatingSystem;

import java.net.InetAddress;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class MetricsService {
    private final SystemInfo si = new SystemInfo();
    private final CentralProcessor cpu = si.getHardware().getProcessor();
    private long[] prevCpuTicks = cpu.getSystemCpuLoadTicks();
    private long[][] prevProcTicks = cpu.getProcessorCpuLoadTicks();
    private final Object cpuTickLock = new Object(); // <— kunci ringkas untuk update ticks

    private final GlobalMemory mem = si.getHardware().getMemory();
    private final OperatingSystem os = si.getOperatingSystem();
    private final Sensors sensors = si.getHardware().getSensors();

    // util aman untuk panggil getter NIO yang kadang lempar UnsupportedOperationException di native
    private static long safeLong(LongSupplierThrows action) {
        try { return action.getAsLong(); } catch (Throwable t) { return 0L; }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static String safe(String s) { return s == null ? "" : s; }

    /** Panggilan synchronous (dipakai internal oleh async). */
    public MetricsDTO snapshot(boolean includeNetwork) {
        // ===== CPU % (lindungi update ticks biar tidak balapan antar request) =====
        double cpuUsage;
        double[] perCore;
        synchronized (cpuTickLock) {
            cpuUsage = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100.0;
            prevCpuTicks = cpu.getSystemCpuLoadTicks();

            perCore = cpu.getProcessorCpuLoadBetweenTicks(prevProcTicks);
            prevProcTicks = cpu.getProcessorCpuLoadTicks();
        }

        List<Double> perCorePct = new ArrayList<>(perCore.length);
        for (double v : perCore) perCorePct.add(round2(v * 100.0));

        Double temp = Double.isNaN(sensors.getCpuTemperature()) ? null : round2(sensors.getCpuTemperature());

        CpuDTO cpuDTO = new CpuDTO(
                cpu.getProcessorIdentifier().getName(),
                cpu.getPhysicalProcessorCount(),
                cpu.getLogicalProcessorCount(),
                cpu.getSystemLoadAverage(1)[0],      // -1 jika tak tersedia
                round2(cpuUsage),
                perCorePct,
                temp
        );

        // ===== Memory =====
        long total = mem.getTotal();
        long available = mem.getAvailable();
        long used = total - available;
        MemoryDTO memoryDTO = new MemoryDTO(
                total, used, available, round2(used * 100.0 / Math.max(1, total))
        );

        // ===== Swap =====
        var vm = mem.getVirtualMemory();
        long swapTotal = vm.getSwapTotal();
        long swapUsed = vm.getSwapUsed();
        SwapDTO swapDTO = new SwapDTO(
                swapTotal, swapUsed,
                round2(swapTotal == 0 ? 0.0 : (swapUsed * 100.0 / swapTotal))
        );

        // ===== Storage (NIO) =====
        List<StorageDTO> disks = new ArrayList<>();
        try {
            FileSystem fsys = FileSystems.getDefault();
            for (FileStore fs : fsys.getFileStores()) {
                long tot = safeLong(fs::getTotalSpace);
                long usable = safeLong(fs::getUsableSpace);
                double usedPct = tot == 0 ? 0.0 : (100.0 * (tot - usable) / tot);

                String name = safe(fs.name());
                if (name.isBlank()) name = safe(fs.toString());
                String type = safe(fs.type());

                disks.add(new StorageDTO(
                        name, type, tot, usable, round2(usedPct)
                ));
            }
        } catch (Exception ignore) {}

        // ===== Network (opsional) =====
        List<NetworkDTO> nets = List.of();
        if (includeNetwork) {
            List<NetworkDTO> tmp = new ArrayList<>();
            for (NetworkIF nif : si.getHardware().getNetworkIFs(true)) {
                String ipv4 = Arrays.stream(nif.getIPv4addr()).findFirst().orElse("");
                String ipv6 = Arrays.stream(nif.getIPv6addr()).findFirst().orElse("");
                tmp.add(new NetworkDTO(
                        nif.getName(),
                        nif.getMacaddr(),
                        ipv4,
                        ipv6,
                        nif.getBytesRecv(),
                        nif.getBytesSent()
                ));
            }
            nets = tmp;
        }

        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }

        long uptimeSec = si.getOperatingSystem().getSystemUptime();

        return new MetricsDTO(
                Instant.now(),
                hostname,
                os.toString(),
                uptimeSec,
                cpuDTO,
                memoryDTO,
                swapDTO,
                disks,
                nets
        );
    }

    /** Versi async untuk dipakai handler Discord. */
    @Async("commandExecutor")
    public CompletableFuture<MetricsDTO> snapshotAsync(boolean includeNetwork) {
        try {
            return CompletableFuture.completedFuture(snapshot(includeNetwork));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @FunctionalInterface
    private interface LongSupplierThrows { long getAsLong() throws Exception; }
}
