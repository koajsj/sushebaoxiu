package com.campus.repair.utils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Business timestamps match the existing Shanghai DATETIME(0) storage. */
public final class BusinessTime {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BusinessTime() {}

    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), ZONE).withNano(0);
    }
}
