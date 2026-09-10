package com.vendor.arconnector.notify;

import com.vendor.arconnector.domain.Notification;
import com.vendor.arconnector.service.DashboardService.PersonStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Decide qué avisos tocan hoy a partir de la situación de cada persona.
 * Lógica pura, sin Spring:
 *
 *  - GAPS_REMINDER a la persona si tiene huecos cuyo último día de envío
 *    está a {@code daysBeforeCutoff} días o menos (incluido hoy).
 *  - REJECTIONS a la persona si tiene días rechazados por Activity Report
 *    en el periodo abierto.
 *  - MANAGER_SUMMARY a cada manager si hay algo que resolver y algún corte
 *    está a {@code daysBeforeCutoff} días o menos.
 *
 * Cada aviso se envía como mucho una vez al día por destinatario y tipo.
 */
public final class NotificationPlanner {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Planned(Notification.Kind kind, String recipientEmail, Long personId, String subject, String body, LocalDate deadline) {
    }

    private NotificationPlanner() {
    }

    public static List<Planned> plan(LocalDate today, int daysBeforeCutoff, List<PersonStatus> statuses,
                                     List<String> managerEmails, String panelUrl) {
        List<Planned> planned = new ArrayList<>();
        LocalDate horizon = today.plusDays(daysBeforeCutoff);

        for (PersonStatus p : statuses) {
            if (p.email() == null || p.email().isBlank()) {
                continue;
            }
            List<LocalDate> urgentGaps = p.gapDates().stream()
                    .filter(d -> p.nextGapDeadline() != null)
                    .toList();
            if (p.nextGapDeadline() != null && !p.nextGapDeadline().isAfter(horizon) && !urgentGaps.isEmpty()) {
                planned.add(new Planned(Notification.Kind.GAPS_REMINDER, p.email(), p.personId(),
                        "Imputaciones pendientes: " + urgentGaps.size() + " día(s) sin imputar, fecha límite " + DATE.format(p.nextGapDeadline()),
                        gapsBody(p, urgentGaps, panelUrl), p.nextGapDeadline()));
            }
            if (!p.rejectedDates().isEmpty()) {
                planned.add(new Planned(Notification.Kind.REJECTIONS, p.email(), p.personId(),
                        "Activity Report ha rechazado " + p.rejectedDates().size() + " día(s) de tus imputaciones",
                        rejectionsBody(p, panelUrl), null));
            }
        }

        List<PersonStatus> toFix = statuses.stream().filter(PersonStatus::hasSomethingToFix).toList();
        LocalDate nearestDeadline = toFix.stream().map(PersonStatus::nextGapDeadline)
                .filter(d -> d != null).min(Comparator.naturalOrder()).orElse(null);
        boolean cutoffNear = nearestDeadline != null && !nearestDeadline.isAfter(horizon);
        boolean hasRejectionsOrExcluded = toFix.stream().anyMatch(p -> !p.rejectedDates().isEmpty() || !p.excludedDates().isEmpty());
        if (!toFix.isEmpty() && (cutoffNear || hasRejectionsOrExcluded)) {
            String subject = "Situación de imputaciones: " + toFix.size() + " persona(s) con algo que resolver"
                    + (nearestDeadline != null ? ", corte el " + DATE.format(nearestDeadline) : "");
            String body = summaryBody(today, toFix, panelUrl);
            for (String email : managerEmails) {
                planned.add(new Planned(Notification.Kind.MANAGER_SUMMARY, email, null, subject, body, nearestDeadline));
            }
        }
        return planned;
    }

    private static String gapsBody(PersonStatus p, List<LocalDate> gaps, String panelUrl) {
        return "Hola " + p.displayName() + ",\n\n"
                + "Tienes " + gaps.size() + " día(s) laborables sin imputación y sin plantilla por defecto:\n"
                + gaps.stream().map(DAY::format).collect(Collectors.joining(", ")) + "\n\n"
                + "El último día para enviarlos a Activity Report es el " + DATE.format(p.nextGapDeadline()) + ". "
                + "Después de esa fecha BBVA ya no los admite.\n\n"
                + "Regístralos en el panel: " + panelUrl + "/mi-mes\n"
                + (p.hasTemplate() ? "" : "Si tu asignación es siempre la misma, pide a tu manager que te configure una plantilla por defecto y no tendrás que volver a imputar a mano.\n");
    }

    private static String rejectionsBody(PersonStatus p, String panelUrl) {
        return "Hola " + p.displayName() + ",\n\n"
                + "Activity Report ha rechazado tus imputaciones de estos días:\n"
                + p.rejectedDates().stream().map(DAY::format).collect(Collectors.joining(", ")) + "\n\n"
                + "Revisa el motivo en el panel y corrígelos antes del corte; se volverán a enviar automáticamente: "
                + panelUrl + "/mi-mes\n";
    }

    private static String summaryBody(LocalDate today, List<PersonStatus> toFix, String panelUrl) {
        StringBuilder sb = new StringBuilder("Situación a " + DATE.format(today) + ":\n\n");
        for (PersonStatus p : toFix) {
            sb.append("- ").append(p.displayName()).append(" (").append(p.uid()).append("): ");
            List<String> parts = new ArrayList<>();
            if (!p.gapDates().isEmpty()) {
                parts.add(p.gapDates().size() + " hueco(s) [" + p.gapDates().stream().map(DAY::format).collect(Collectors.joining(", "))
                        + "], límite " + DATE.format(p.nextGapDeadline()));
            }
            if (!p.rejectedDates().isEmpty()) {
                parts.add(p.rejectedDates().size() + " rechazado(s) [" + p.rejectedDates().stream().map(DAY::format).collect(Collectors.joining(", ")) + "]");
            }
            if (!p.excludedDates().isEmpty()) {
                parts.add(p.excludedDates().size() + " excluido(s) del fichero (" + String.join(", ", p.problemCodes()) + ")");
            }
            sb.append(String.join("; ", parts)).append("\n");
        }
        sb.append("\nPanel de situación: ").append(panelUrl).append("/situacion\n");
        return sb.toString();
    }
}
