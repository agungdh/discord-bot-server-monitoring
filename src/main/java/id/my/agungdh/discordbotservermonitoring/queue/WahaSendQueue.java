package id.my.agungdh.discordbotservermonitoring.queue;

import id.my.agungdh.discordbotservermonitoring.DTO.waha.SendTextResponse;
import id.my.agungdh.discordbotservermonitoring.service.WahaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.*;

@Component
public class WahaSendQueue {

    private static final Logger log = LoggerFactory.getLogger(WahaSendQueue.class);

    private final WahaService wahaService;
    private final BlockingQueue<Job> queue;
    private final long perMessageDelayMs;
    private final ExecutorService worker;

    public WahaSendQueue(
            WahaService wahaService,
            @Value("${waha.reminder.per-message-delay-ms:10000}") long perMessageDelayMs
    ) {
        this.wahaService = wahaService;
        this.perMessageDelayMs = Math.max(0, perMessageDelayMs);
        this.queue = new LinkedBlockingQueue<>();
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "waha-queue-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /** Enqueue satu nomor */
    public void enqueue(String phone, String text) {
        queue.offer(new Job(phone, text));
    }

    /** Enqueue banyak nomor */
    public void enqueueAll(List<String> phones, String text) {
        if (phones == null) return;
        for (String p : phones) {
            if (p != null && !p.isBlank()) {
                enqueue(p.trim(), text);
            }
        }
    }

    @PostConstruct
    void startWorker() {
        worker.submit(this::loop);
        log.info("WahaSendQueue worker started (delay {} ms)", perMessageDelayMs);
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
        log.info("WahaSendQueue worker stopped");
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Job job = queue.take();
                try {
                    SendTextResponse res = wahaService.sendTextAsync(job.phone, job.text)
                            .orTimeout(35, TimeUnit.SECONDS)
                            .exceptionally(ex -> new SendTextResponse(false, null, ex.getMessage()))
                            .join();

                    log.info("Sent to {} -> success={} id={} err={}",
                            job.phone, res.success(), res.messageId(), res.error());
                } catch (Exception e) {
                    log.error("Send failed to {}: {}", job.phone, e.getMessage());
                }

                if (perMessageDelayMs > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(perMessageDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                log.error("Worker error: {}", t.getMessage(), t);
            }
        }
    }

    private record Job(String phone, String text) {}
}
