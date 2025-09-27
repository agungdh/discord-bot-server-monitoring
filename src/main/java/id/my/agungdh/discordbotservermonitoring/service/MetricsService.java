// service/MetricsService.java (adapter)
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

    // Ambil node pertama sebagai default (drop-in replacement)
    public MetricsDTO snapshot(boolean includeNetwork) {
        if (props.getNodes().isEmpty())
            throw new IllegalStateException("monitoring.nodes kosong");
        var n = props.getNodes().get(0);
        return nodeSvc.snapshotFromUrl(n.getName() != null ? n.getName() : n.getUrl(), n.getUrl(), includeNetwork);
    }

    @Async("commandExecutor")
    public CompletableFuture<MetricsDTO> snapshotAsync(boolean includeNetwork) {
        try {
            return CompletableFuture.completedFuture(snapshot(includeNetwork));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
