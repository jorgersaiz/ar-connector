package com.vendor.arconnector.activities;

import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.DayOrigin;
import com.vendor.arconnector.domain.DefaultTemplate;
import com.vendor.arconnector.domain.Person;

import java.time.LocalDate;
import java.util.*;

/**
 * Reconstruye el periodo abierto completo para un envío: para cada persona en
 * perímetro y cada fecha todavía modificable (y no futura), decide qué se
 * envía. Es lógica pura, sin Spring ni base de datos, para poder testearla
 * contra las reglas del documento.
 *
 * Por qué se envía TODO el periodo cada vez y no solo los cambios: Activity
 * Report solo procesa el último fichero recibido cada día y sobreescribe el
 * día completo de cada persona, así que un fichero parcial borraría lo que no
 * incluye.
 *
 * Orden de decisión para cada persona/día:
 *  1. Fin de semana o festivo nacional  -> no se envía nada.
 *  2. Existe un ActivityDay explícito   -> se envía tal cual (trabajado o no imputable).
 *  3. Festivo local                     -> se envía como día no imputable.
 *  4. Hay plantilla por defecto vigente -> se materializa como día trabajado y se envía.
 *  5. Si no                             -> hueco (Gap), no se envía y hay que reclamarlo.
 */
public final class PeriodSnapshotBuilder {

    private final ModifiablePeriodPolicy periodPolicy;
    private final WorkCalendar calendar;

    public PeriodSnapshotBuilder(ModifiablePeriodPolicy periodPolicy, WorkCalendar calendar) {
        this.periodPolicy = Objects.requireNonNull(periodPolicy);
        this.calendar = Objects.requireNonNull(calendar);
    }

    public PeriodSnapshot build(LocalDate submissionDate,
                                Collection<Person> persons,
                                Collection<ActivityDay> existingDays,
                                Collection<DefaultTemplate> templates) {

        Map<String, ActivityDay> existingByKey = new HashMap<>();
        for (ActivityDay d : existingDays) {
            existingByKey.put(key(d.getPerson().getUid(), d.getActivityDate()), d);
        }
        Map<String, List<DefaultTemplate>> templatesByUid = new HashMap<>();
        for (DefaultTemplate t : templates) {
            templatesByUid.computeIfAbsent(t.getPerson().getUid(), u -> new ArrayList<>()).add(t);
        }

        List<LocalDate> dates = periodPolicy.submittableActivityDates(submissionDate);
        List<DaySubmission> days = new ArrayList<>();
        List<ActivityDay> generated = new ArrayList<>();
        List<Gap> gaps = new ArrayList<>();

        for (Person person : persons) {
            for (LocalDate date : dates) {
                if (!person.isInPerimeterOn(date)) {
                    continue;
                }
                DayClassification classification = calendar.classify(person.getGeography(), date);
                if (classification.isSkipped()) {
                    continue;
                }

                ActivityDay explicit = existingByKey.get(key(person.getUid(), date));
                if (explicit != null) {
                    days.add(DaySubmission.from(explicit));
                    continue;
                }

                if (classification == DayClassification.LOCAL_HOLIDAY) {
                    ActivityDay holidayDay = ActivityDay.nonWorking(person, date, DayOrigin.TEMPLATE, "Festivo local");
                    generated.add(holidayDay);
                    days.add(DaySubmission.from(holidayDay));
                    continue;
                }

                // Si varias plantillas cubren el día, manda la que empezó más tarde (la más reciente)
                Optional<DefaultTemplate> template = templatesByUid.getOrDefault(person.getUid(), List.of()).stream()
                        .filter(t -> t.appliesOn(date))
                        .max(Comparator.comparing(DefaultTemplate::getValidFrom));
                if (template.isPresent() && !template.get().getLines().isEmpty()) {
                    ActivityDay materialized = template.get().materialize(date);
                    generated.add(materialized);
                    days.add(DaySubmission.from(materialized));
                    continue;
                }

                gaps.add(new Gap(person.getUid(), date, periodPolicy.lastSubmissionDate(date)));
            }
        }

        return new PeriodSnapshot(days, generated, gaps);
    }

    private static String key(String uid, LocalDate date) {
        return uid + "|" + date;
    }
}
