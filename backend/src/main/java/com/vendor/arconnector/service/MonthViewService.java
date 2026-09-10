package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.ModifiablePeriodPolicy;
import com.vendor.arconnector.activities.MonthViewBuilder;
import com.vendor.arconnector.activities.WorkCalendar;
import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.repository.ActivityDayRepository;
import com.vendor.arconnector.repository.DefaultTemplateRepository;
import com.vendor.arconnector.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Carga los datos de un mes de una persona y delega en {@link MonthViewBuilder}. */
@Service
public class MonthViewService {

    private final ActivityDayRepository activityDayRepository;
    private final DefaultTemplateRepository templateRepository;
    private final HolidayRepository holidayRepository;
    private final Clock clock;

    public MonthViewService(ActivityDayRepository activityDayRepository, DefaultTemplateRepository templateRepository,
                            HolidayRepository holidayRepository, Clock clock) {
        this.activityDayRepository = activityDayRepository;
        this.templateRepository = templateRepository;
        this.holidayRepository = holidayRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MonthViewBuilder.DayView> month(Person person, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        WorkCalendar calendar = new WorkCalendar(holidayRepository.findByDateBetween(from, to));
        return new MonthViewBuilder(new ModifiablePeriodPolicy(), calendar).build(
                person, month, LocalDate.now(clock),
                activityDayRepository.findByPersonInRangeWithLines(person, from, to),
                templateRepository.findByPersonWithLines(person));
    }
}
