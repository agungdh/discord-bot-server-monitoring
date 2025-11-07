package id.my.agungdh.discordbotservermonitoring.controller;

import id.my.agungdh.discordbotservermonitoring.service.AbsenReminderState;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/absen-reminder")
public class AbsenReminderController {

    private final AbsenReminderState state;

    public AbsenReminderController(AbsenReminderState state) {
        this.state = state;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "enabledToday", state.isEnabledToday()
        );
    }

    @PostMapping("/on")
    public Map<String, Object> turnOn() {
        state.setEnabledToday(true);
        return Map.of("enabledToday", true, "message", "Absen reminder diaktifkan");
    }

    @PostMapping("/off")
    public Map<String, Object> turnOff() {
        state.setEnabledToday(false);
        return Map.of("enabledToday", false, "message", "Absen reminder dimatikan untuk hari ini");
    }
}
