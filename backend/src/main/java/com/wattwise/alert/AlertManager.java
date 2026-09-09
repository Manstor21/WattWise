package com.wattwise.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gestor de observers: difunde un {@link PriceAlertContext} a cada
 * {@link AlertObserver} registrado y recoge las alertas que se han disparado.
 * Los observers se conectan automáticamente desde el contexto de Spring.
 */
@Component
public class AlertManager {

    private static final Logger log = LoggerFactory.getLogger(AlertManager.class);

    private final List<AlertObserver> observers;

    public AlertManager(List<AlertObserver> observers) {
        this.observers = observers;
    }

    /** Evalúa todos los observers y devuelve las alertas descritas que se hayan disparado. */
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