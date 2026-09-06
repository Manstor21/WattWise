package com.wattwise.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Observer manager: broadcasts a {@link PriceAlertContext} to every registered
 * {@link AlertObserver} and collects the alerts that fired. Observers are wired
 * automatically from the Spring context.
 */
@Component
public class AlertManager {

    private static final Logger log = LoggerFactory.getLogger(AlertManager.class);

    private final List<AlertObserver> observers;

    public AlertManager(List<AlertObserver> observers) {
        this.observers = observers;
    }

    /** Evaluate all observers and return the described alerts that fired. */
    public List<String> checkAndNotify(PriceAlertContext context) {
        if (context == null) {
            return List.of();
        }
        return observers.stream()
                .filter(o -> o.evaluate(context))
                .map(observer -> {
                    String alert = observer.name() + " — " + observer.describe(context);
                    log.info("Alert fired: {}", alert);
                    return alert;
                })
                .toList();
    }
}