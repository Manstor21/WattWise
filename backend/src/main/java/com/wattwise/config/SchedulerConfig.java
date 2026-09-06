package com.wattwise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling (already done via WattWiseApplication, kept as a
 * dedicated config class so the schedule stays discoverable) and exposes the
 * cron expression from configuration.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Value("${wattwise.scheduler.price-fetch-cron:0 15 20 * * *}")
    private String priceFetchCron;

    public String getPriceFetchCron() {
        return priceFetchCron;
    }
}