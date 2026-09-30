package com.campus.repair.config;

import com.campus.repair.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseStartupCheck implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseStartupCheck.class);
    private final HealthService healthService;

    public DatabaseStartupCheck(HealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        healthService.check();
        LOG.info("Database connection verified");
    }
}
