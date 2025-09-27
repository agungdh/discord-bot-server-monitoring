// service/MetricsService.java
package id.my.agungdh.discordbotservermonitoring.service;

import id.my.agungdh.discordbotservermonitoring.DTO.monitoring.MetricsDTO;
import id.my.agungdh.discordbotservermonitoring.config.MonitoringProps;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class MetricsService {

    private final NodeMetricsService nodeSvc;
    private final MonitoringProps props;

    public MetricsService(NodeMetricsService nodeSvc, MonitoringProps props) {
        this.nodeSvc = nodeSvc;
        this.props = props;
    }

    public MetricsDTO snapshot(boolean includeNetwork) {
        if (props.getNodes().isEmpty())
            throw new IllegalStateException("monitoring.nodes kosong");
        var n = props.getNodes().get(0);
        String key = (n.getName() != null && !n.getName().isBlank()) ? n.getName() : n.getUrl();
        return nodeSvc.snapshotFromUrl(key, n.getUrl(), includeNetwork);
    }

    @Async("commandExecutor")
    public CompletableFuture<MetricsDTO> snapshotAsync(boolean includeNetwork) {
        try { return CompletableFuture.completedFuture(snapshot(includeNetwork)); }
        catch (Exception e) { return CompletableFuture.failedFuture(e); }
    }
}
