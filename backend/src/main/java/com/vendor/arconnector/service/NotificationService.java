package com.vendor.arconnector.service;

import com.vendor.arconnector.domain.AppUser;
import com.vendor.arconnector.domain.Notification;
import com.vendor.arconnector.domain.UserRole;
import com.vendor.arconnector.notify.NotificationException;
import com.vendor.arconnector.notify.NotificationPlanner;
import com.vendor.arconnector.notify.NotificationProperties;
import com.vendor.arconnector.notify.Notifier;
import com.vendor.arconnector.repository.AppUserRepository;
import com.vendor.arconnector.repository.NotificationRepository;
import com.vendor.arconnector.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Planifica y envía los avisos del día, registrando cada uno en la tabla notification. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public record Detail(Notification.Kind kind, String recipientEmail, String subject) {
    }

    public record RunResult(int sent, int skipped, Notification.Channel channel, List<Detail> details) {
    }

    public record Config(Notification.Channel channel, int daysBeforeCutoff, boolean mailEnabled) {
    }

    private final DashboardService dashboardService;
    private final Notifier notifier;
    private final NotificationRepository notificationRepository;
    private final AppUserRepository userRepository;
    private final PersonRepository personRepository;
    private final NotificationProperties props;
    private final String panelUrl;
    private final Clock clock;

    public NotificationService(DashboardService dashboardService, Notifier notifier, NotificationRepository notificationRepository,
                               AppUserRepository userRepository, PersonRepository personRepository, NotificationProperties props,
                               @Value("${panel.url:http://localhost:4200}") String panelUrl, Clock clock) {
        this.dashboardService = dashboardService;
        this.notifier = notifier;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.personRepository = personRepository;
        this.props = props;
        this.panelUrl = panelUrl;
        this.clock = clock;
    }

    public Config config() {
        return new Config(notifier.channel(), props.daysBeforeCutoffOrDefault(), props.mailEnabled());
    }

    public RunResult runToday() {
        LocalDate today = LocalDate.now(clock);
        List<String> managerEmails = userRepository.findAllWithPerson().stream()
                .filter(u -> u.isActive() && u.getRole() == UserRole.MANAGER)
                .map(AppUser::getEmail)
                .toList();

        List<NotificationPlanner.Planned> planned = NotificationPlanner.plan(
                today, props.daysBeforeCutoffOrDefault(), dashboardService.personStatuses(today), managerEmails, panelUrl);

        Instant dayStart = today.atStartOfDay(clock.getZone()).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        Set<String> alreadySent = new HashSet<>();
        for (Object[] row : notificationRepository.findRecipientAndKindBetween(dayStart, dayEnd)) {
            alreadySent.add(row[0] + "|" + row[1]);
        }

        int sent = 0;
        int skipped = 0;
        List<Detail> details = new ArrayList<>();
        for (NotificationPlanner.Planned p : planned) {
            String key = p.recipientEmail() + "|" + p.kind();
            if (alreadySent.contains(key)) {
                skipped++;
                continue;
            }
            try {
                notifier.send(p.recipientEmail(), p.subject(), p.body());
            } catch (NotificationException e) {
                log.error("Aviso {} a {} no entregado: {}", p.kind(), p.recipientEmail(), e.getMessage());
                skipped++;
                continue;
            }
            notificationRepository.save(new Notification(Instant.now(clock), notifier.channel(), p.kind(), p.recipientEmail(),
                    p.personId() == null ? null : personRepository.findById(p.personId()).orElse(null),
                    p.subject(), p.body(), p.deadline()));
            alreadySent.add(key);
            sent++;
            details.add(new Detail(p.kind(), p.recipientEmail(), p.subject()));
        }
        log.info("Avisos de hoy: {} enviados, {} omitidos (canal {})", sent, skipped, notifier.channel());
        return new RunResult(sent, skipped, notifier.channel(), details);
    }
}
