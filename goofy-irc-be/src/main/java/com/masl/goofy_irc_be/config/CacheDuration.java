package com.masl.goofy_irc_be.config;

import lombok.Getter;
import org.springframework.http.CacheControl;

import java.util.concurrent.TimeUnit;

@Getter
public enum CacheDuration {
    NONE(0),
    SHORT(900), // 15 minutes
    NORMAL(14400), // 4 hours
    LONG(43200), // 12 hours
    VERY_LONG(604800); // 7 days

    private final int durationInSeconds;

    CacheDuration(int durationInSeconds) {
        this.durationInSeconds = durationInSeconds;
    }

    public CacheControl getCacheControl() {
        if (durationInSeconds < 1)
            return CacheControl.noStore();
        return CacheControl.maxAge(durationInSeconds, TimeUnit.SECONDS);
    }
}
