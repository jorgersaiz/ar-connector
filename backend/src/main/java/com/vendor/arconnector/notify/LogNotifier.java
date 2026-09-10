package com.vendor.arconnector.notify;

import com.vendor.arconnector.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sin SMTP configurado: el aviso se registra en el log (y en la tabla notification) pero no se envía. */
public class LogNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LogNotifier.class);

    @Override
    public Notification.Channel channel() {
        return Notification.Channel.LOG;
    }

    @Override
    public void send(String recipientEmail, String subject, String body) {
        log.info("AVISO (sin correo configurado) para {}: {}\n{}", recipientEmail, subject, body);
    }
}
