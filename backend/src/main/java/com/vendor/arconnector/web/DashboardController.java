package com.vendor.arconnector.web;

import com.vendor.arconnector.domain.Notification;
import com.vendor.arconnector.repository.NotificationRepository;
import com.vendor.arconnector.service.DashboardService;
import com.vendor.arconnector.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Situación del periodo y avisos (managers). */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('MANAGER')")
public class DashboardController {

    public record NotificationDto(Long id, Instant sentAt, Notification.Channel channel, Notification.Kind kind,
                                  String recipientEmail, Long personId, String personDisplayName, String subject,
                                  String body, LocalDate deadline) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getSentAt(), n.getChannel(), n.getKind(), n.getRecipientEmail(),
                    n.getPerson() == null ? null : n.getPerson().getId(),
                    n.getPerson() == null ? null : n.getPerson().getDisplayName(),
                    n.getSubject(), n.getBody(), n.getDeadline());
        }
    }

    private final DashboardService dashboardService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public DashboardController(DashboardService dashboardService, NotificationService notificationService,
                               NotificationRepository notificationRepository) {
        this.dashboardService = dashboardService;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/dashboard")
    public DashboardService.Dashboard dashboard() {
        return dashboardService.build();
    }

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public List<NotificationDto> notifications(@RequestParam(defaultValue = "100") int limit) {
        return notificationRepository.findRecent(PageRequest.of(0, Math.min(Math.max(limit, 1), 500))).stream()
                .map(NotificationDto::from).toList();
    }

    @PostMapping("/notifications/run")
    public NotificationService.RunResult run() {
        return notificationService.runToday();
    }

    @GetMapping("/notifications/config")
    public NotificationService.Config config() {
        return notificationService.config();
    }
}
