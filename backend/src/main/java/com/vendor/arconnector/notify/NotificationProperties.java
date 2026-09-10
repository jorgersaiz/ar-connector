package com.vendor.arconnector.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param daysBeforeCutoff con cuántos días de antelación al último día de envío se avisa de los huecos
 * @param mail             correo: activado y remitente. El SMTP se configura en spring.mail.*
 */
@ConfigurationProperties(prefix = "notifications")
public record NotificationProperties(Integer daysBeforeCutoff, Mail mail) {

    public record Mail(Boolean enabled, String from) {
        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }

    public int daysBeforeCutoffOrDefault() {
        return daysBeforeCutoff == null ? 2 : daysBeforeCutoff;
    }

    public boolean mailEnabled() {
        return mail != null && mail.isEnabled();
    }

    public String mailFrom() {
        return mail == null || mail.from() == null ? "ar-connector@arktic.local" : mail.from();
    }
}
