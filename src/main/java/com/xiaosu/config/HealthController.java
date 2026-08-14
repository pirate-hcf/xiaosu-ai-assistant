package com.xiaosu.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final ApplicationHealthService healthService;

    public HealthController(ApplicationHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/live")
    public LiveStatus live() {
        return new LiveStatus("UP");
    }

    @GetMapping("/ready")
    public ApplicationHealthService.ReadinessStatus ready() {
        return healthService.readiness();
    }

    public record LiveStatus(String status) {
    }
}
