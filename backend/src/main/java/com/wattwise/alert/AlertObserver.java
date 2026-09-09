package com.wattwise.alert;

/**
 * Contrato de un observer: las implementaciones evalúan un {@link PriceAlertContext}
 * y deciden si se cumple su condición de alerta. AlertManager difunde cada contexto
 * a todos los observers registrados (patrón Observer).
 */
public interface AlertObserver {

    /** Identificador estable usado en los mensajes de alerta y en los logs. */
    String name();

    /** Evalúa el contexto y devuelve true cuando la alerta debe dispararse. */
    boolean evaluate(PriceAlertContext context);

    /** Descripción legible de la condición que se ha disparado. */
    String describe(PriceAlertContext context);
}