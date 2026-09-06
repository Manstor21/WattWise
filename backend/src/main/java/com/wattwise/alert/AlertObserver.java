package com.wattwise.alert;

/**
 * Observer contract: implementations evaluate a {@link PriceAlertContext} and
 * decide whether their alert condition is met. AlertManager broadcasts each
 * context to all registered observers (Observer pattern).
 */
public interface AlertObserver {

    /** Stable identifier used in alert messages and logs. */
    String name();

    /** Evaluate the context and return true when the alert should fire. */
    boolean evaluate(PriceAlertContext context);

    /** Human-readable description of the condition that fired. */
    String describe(PriceAlertContext context);
}