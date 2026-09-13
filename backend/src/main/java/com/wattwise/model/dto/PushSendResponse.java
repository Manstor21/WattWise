package com.wattwise.model.dto;

/**
 * Resultado de {@code POST /api/push/send}: número de dispositivos a los que
 * el emisor consiguió entregar la notificación (0 si todos los tokens fallaron).
 */
public class PushSendResponse {

    private int recipients;

    public PushSendResponse() {
    }

    public PushSendResponse(int recipients) {
        this.recipients = recipients;
    }

    public int getRecipients() {
        return recipients;
    }

    public void setRecipients(int recipients) {
        this.recipients = recipients;
    }
}