package com.vendor.arconnector.notify;

import com.vendor.arconnector.domain.Notification;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Envío por correo con el SMTP de spring.mail.* (activado con notifications.mail.enabled=true). */
public class MailNotifier implements Notifier {

    private final JavaMailSender mailSender;
    private final String from;

    public MailNotifier(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public Notification.Channel channel() {
        return Notification.Channel.EMAIL;
    }

    @Override
    public void send(String recipientEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new NotificationException("No se ha podido enviar el correo a " + recipientEmail + ": " + e.getMessage(), e);
        }
    }
}
