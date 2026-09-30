package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.service.HealthService;
import com.campus.repair.vo.HealthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public Result<HealthVO> health() {
        return Result.success(healthService.check());
    }
}
