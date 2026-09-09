package com.wattwise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita la programación de Spring (ya hecha vía WattWiseApplication; se mantiene
 * como clase de configuración dedicada para que el schedule siga siendo localizable)
 * y expone la expresión cron desde la configuración.
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