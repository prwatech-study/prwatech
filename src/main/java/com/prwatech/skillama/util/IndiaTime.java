package com.prwatech.skillama.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Wall-clock timestamps for Skillama (IST). Use instead of {@link LocalDateTime#now()}. */
public final class IndiaTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private IndiaTime() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
