package com.vendor.arconnector.web.dto;

import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.DayKind;
import com.vendor.arconnector.domain.DayOrigin;

import java.time.LocalDate;
import java.util.List;

/** Día de actividad explícito de una persona, con el resultado del último feedback. */
public record DayDto(
        Long id,
        LocalDate date,
        DayKind kind,
        DayOrigin origin,
        String note,
        LocalDate lastSentDate,
        LocalDate lastFeedbackDate,
        String rejectReason,
        boolean hasRejections,
        int totalMinutes,
        List<LineDto> lines
) {
    public static DayDto from(ActivityDay d) {
        return new DayDto(d.getId(), d.getActivityDate(), d.getKind(), d.getOrigin(), d.getNote(),
                d.getLastSentDate(), d.getLastFeedbackDate(), d.getRejectReason(), d.hasRejections(),
                d.totalMinutes(), d.getLines().stream().map(LineDto::from).toList());
    }
}
