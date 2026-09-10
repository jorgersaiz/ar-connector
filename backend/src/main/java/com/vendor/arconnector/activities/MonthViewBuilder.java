package com.vendor.arconnector.activities;

import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.DayKind;
import com.vendor.arconnector.domain.DayOrigin;
import com.vendor.arconnector.domain.DefaultTemplate;
import com.vendor.arconnector.domain.Person;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Vista de un mes de una persona para el panel: para cada día, qué se
 * enviaría (día explícito, plantilla, festivo local o nada), si está bloqueado
 * por haber cerrado el periodo modificable y el resultado del último envío.
 * Lógica pura, sin Spring.
 */
public final class MonthViewBuilder {

    public enum Source {
        /** Día explícito (manual o importado). */
        EXPLICIT,
        /** Se imputaría con la plantilla por defecto (no persistido todavía). */
        TEMPLATE,
        /** Festivo local: iría como día no imputable. */
        HOLIDAY,
        /** Nada que enviar. */
        NONE
    }

    public record DayView(
            LocalDate date,
            DayClassification classification,
            Source source,
            DayKind kind,
            DayOrigin origin,
            String note,
            List<LineSubmissionView> lines,
            int totalMinutes,
            LocalDate lastSentDate,
            LocalDate lastFeedbackDate,
            boolean hasRejections,
            String rejectReason,
            boolean gap,
            boolean locked,
            LocalDate lastSubmissionDate,
            boolean future
    ) {
    }

    public record LineSubmissionView(String taskTypeCode, String sdaProjectId, String jiraIssueId, String nonSdaProject,
                                     int minutes, String rejectReason) {
    }

    private final ModifiablePeriodPolicy periodPolicy;
    private final WorkCalendar calendar;

    public MonthViewBuilder(ModifiablePeriodPolicy periodPolicy, WorkCalendar calendar) {
        this.periodPolicy = periodPolicy;
        this.calendar = calendar;
    }

    public List<DayView> build(Person person, YearMonth month, LocalDate today,
                               Collection<ActivityDay> explicitDays, Collection<DefaultTemplate> templates) {
        Map<LocalDate, ActivityDay> explicitByDate = new HashMap<>();
        for (ActivityDay d : explicitDays) {
            explicitByDate.put(d.getActivityDate(), d);
        }

        List<DayView> views = new ArrayList<>();
        for (int i = 1; i <= month.lengthOfMonth(); i++) {
            LocalDate date = month.atDay(i);
            DayClassification classification = calendar.classify(person.getGeography(), date);
            LocalDate lastSubmission = periodPolicy.lastSubmissionDate(date);
            boolean locked = today.isAfter(lastSubmission);
            boolean future = date.isAfter(today);
            boolean inPerimeter = person.isInPerimeterOn(date);

            ActivityDay explicit = explicitByDate.get(date);
            if (explicit != null) {
                views.add(new DayView(date, classification, Source.EXPLICIT, explicit.getKind(), explicit.getOrigin(),
                        explicit.getNote(), lines(explicit), explicit.totalMinutes(), explicit.getLastSentDate(),
                        explicit.getLastFeedbackDate(), explicit.hasRejections(), explicit.getRejectReason(),
                        false, locked, lastSubmission, future));
                continue;
            }

            if (classification.isSkipped() || !inPerimeter) {
                views.add(empty(date, classification, Source.NONE, false, locked, lastSubmission, future));
                continue;
            }
            if (classification == DayClassification.LOCAL_HOLIDAY) {
                views.add(new DayView(date, classification, Source.HOLIDAY, DayKind.NON_WORKING, DayOrigin.TEMPLATE,
                        "Festivo local", List.of(), 0, null, null, false, null, false, locked, lastSubmission, future));
                continue;
            }

            Optional<DefaultTemplate> template = templates.stream().filter(t -> t.appliesOn(date)).findFirst();
            if (template.isPresent() && !template.get().getLines().isEmpty()) {
                ActivityDay materialized = template.get().materialize(date);
                views.add(new DayView(date, classification, Source.TEMPLATE, DayKind.WORKED, DayOrigin.TEMPLATE, null,
                        lines(materialized), materialized.totalMinutes(), null, null, false, null,
                        false, locked, lastSubmission, future));
                continue;
            }

            boolean gap = !future;
            views.add(empty(date, classification, Source.NONE, gap, locked, lastSubmission, future));
        }
        return views;
    }

    private static DayView empty(LocalDate date, DayClassification classification, Source source, boolean gap,
                                 boolean locked, LocalDate lastSubmission, boolean future) {
        return new DayView(date, classification, source, null, null, null, List.of(), 0, null, null, false, null,
                gap, locked, lastSubmission, future);
    }

    private static List<LineSubmissionView> lines(ActivityDay day) {
        return day.getLines().stream()
                .map(l -> new LineSubmissionView(l.getTaskTypeCode(), l.getSdaProjectId(), l.getJiraIssueId(),
                        l.getNonSdaProject(), l.getMinutes(), l.getRejectReason()))
                .toList();
    }
}
