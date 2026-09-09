package com.vendor.arconnector.activities;

import com.vendor.arconnector.domain.Holiday;
import com.vendor.arconnector.domain.HolidayScope;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Calendario laboral por geografía: fines de semana y festivos. */
public final class WorkCalendar {

    private final Map<String, Map<LocalDate, HolidayScope>> holidaysByGeography = new HashMap<>();

    public WorkCalendar(Collection<Holiday> holidays) {
        for (Holiday h : holidays) {
            holidaysByGeography
                    .computeIfAbsent(h.getGeography(), g -> new HashMap<>())
                    .put(h.getDate(), h.getScope());
        }
    }

    public static WorkCalendar empty() {
        return new WorkCalendar(List.of());
    }

    public DayClassification classify(String geography, LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return DayClassification.WEEKEND;
        }
        HolidayScope scope = holidaysByGeography.getOrDefault(geography, Map.of()).get(date);
        if (scope == HolidayScope.NATIONAL) {
            return DayClassification.NATIONAL_HOLIDAY;
        }
        if (scope == HolidayScope.LOCAL) {
            return DayClassification.LOCAL_HOLIDAY;
        }
        return DayClassification.WORKING;
    }

    public boolean isWorkingDay(String geography, LocalDate date) {
        return classify(geography, date) == DayClassification.WORKING;
    }
}
