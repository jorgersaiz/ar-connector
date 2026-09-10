package com.vendor.arconnector.activities;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Quincenas que todavía se pueden enviar a Activity Report en una fecha dada. */
public final class OpenPeriods {

    public record Period(String label, LocalDate from, LocalDate to, LocalDate lastSubmissionDate, long daysLeft) {
    }

    private OpenPeriods() {
    }

    public static List<Period> on(LocalDate today, ModifiablePeriodPolicy policy) {
        Map<String, List<LocalDate>> byPeriod = new LinkedHashMap<>();
        for (LocalDate d : policy.submittableActivityDates(today)) {
            String key = d.getYear() + "-" + d.getMonthValue() + "-" + (d.getDayOfMonth() <= ModifiablePeriodPolicy.FIRST_HALF_LAST_DAY ? 1 : 2);
            byPeriod.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        List<Period> periods = new ArrayList<>();
        for (List<LocalDate> dates : byPeriod.values()) {
            LocalDate first = dates.get(0);
            LocalDate from = first.getDayOfMonth() <= ModifiablePeriodPolicy.FIRST_HALF_LAST_DAY
                    ? first.withDayOfMonth(1) : first.withDayOfMonth(ModifiablePeriodPolicy.FIRST_HALF_LAST_DAY + 1);
            LocalDate to = first.getDayOfMonth() <= ModifiablePeriodPolicy.FIRST_HALF_LAST_DAY
                    ? first.withDayOfMonth(ModifiablePeriodPolicy.FIRST_HALF_LAST_DAY) : first.withDayOfMonth(first.lengthOfMonth());
            LocalDate last = policy.lastSubmissionDate(first);
            periods.add(new Period(label(first), from, to, last, ChronoUnit.DAYS.between(today, last)));
        }
        return periods;
    }

    static String label(LocalDate anyDateOfPeriod) {
        String month = anyDateOfPeriod.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES"));
        String half = anyDateOfPeriod.getDayOfMonth() <= ModifiablePeriodPolicy.FIRST_HALF_LAST_DAY ? "1ª" : "2ª";
        return half + " quincena de " + month + " " + anyDateOfPeriod.getYear();
    }
}
