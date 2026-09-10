package com.vendor.arconnector.notify;

import com.vendor.arconnector.domain.Notification;

/** Canal de salida de los avisos. La decisión de a quién avisar no vive aquí. */
public interface Notifier {

    Notification.Channel channel();

    /**
     * Envía el aviso.
     *
     * @throws NotificationException si no se ha podido entregar
     */
    void send(String recipientEmail, String subject, String body);
}
