package com.vendor.arconnector.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class NotifierConfig {

    private static final Logger log = LoggerFactory.getLogger(NotifierConfig.class);

    @Bean
    public Notifier notifier(NotificationProperties props, ObjectProvider<JavaMailSender> mailSender) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (props.mailEnabled() && sender != null) {
            log.info("Avisos por correo activados (remitente {})", props.mailFrom());
            return new MailNotifier(sender, props.mailFrom());
        }
        log.warn("Avisos sin correo: no hay SMTP configurado (notifications.mail.enabled / spring.mail.host). Solo se registran.");
        return new LogNotifier();
    }
}
