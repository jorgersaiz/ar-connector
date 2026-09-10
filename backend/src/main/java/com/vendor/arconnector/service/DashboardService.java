package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.*;
import com.vendor.arconnector.domain.ActivityDay;
import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.repository.ActivityDayRepository;
import com.vendor.arconnector.repository.ApiCallRepository;
import com.vendor.arconnector.repository.DefaultTemplateRepository;
import com.vendor.arconnector.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Situación del periodo abierto para el manager: quincenas y sus cortes,
 * cuota, último envío, feedback pendiente y, por persona, huecos, rechazos y
 * días excluidos del fichero.
 */
@Service
public class DashboardService {

    public record Totals(int persons, int personsWithGaps, int gaps, int personsWithRejections, int rejections, int excludedDays) {
    }

    public record PersonStatus(Long personId, String uid, String displayName, String email, boolean hasTemplate,
                               List<LocalDate> gapDates, LocalDate nextGapDeadline,
                               List<LocalDate> rejectedDates, List<LocalDate> excludedDates, List<String> problemCodes) {
        public boolean hasSomethingToFix() {
            return !gapDates.isEmpty() || !rejectedDates.isEmpty() || !excludedDates.isEmpty();
        }
    }

    public record LastUpload(Instant calledAt, LocalDate submissionDate, boolean ok, Integer dayCount, Integer rowCount, String errorMessage) {
    }

    public record Dashboard(LocalDate today, LocalDate submissionDate, List<OpenPeriods.Period> periods,
                            CallQuotaService.QuotaStatus quota, LastUpload lastUpload, List<LocalDate> pendingFeedbackDates,
                            Totals totals, List<PersonStatus> persons) {
    }

    private final PeriodSnapshotService snapshotService;
    private final CallQuotaService quotaService;
    private final FeedbackService feedbackService;
    private final PersonRepository personRepository;
    private final ActivityDayRepository activityDayRepository;
    private final DefaultTemplateRepository templateRepository;
    private final ApiCallRepository apiCallRepository;
    private final ModifiablePeriodPolicy policy = new ModifiablePeriodPolicy();
    private final Clock clock;

    public DashboardService(PeriodSnapshotService snapshotService, CallQuotaService quotaService, FeedbackService feedbackService,
                            PersonRepository personRepository, ActivityDayRepository activityDayRepository,
                            DefaultTemplateRepository templateRepository, ApiCallRepository apiCallRepository, Clock clock) {
        this.snapshotService = snapshotService;
        this.quotaService = quotaService;
        this.feedbackService = feedbackService;
        this.personRepository = personRepository;
        this.activityDayRepository = activityDayRepository;
        this.templateRepository = templateRepository;
        this.apiCallRepository = apiCallRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard build() {
        LocalDate today = LocalDate.now(clock);
        List<OpenPeriods.Period> periods = OpenPeriods.on(today, policy);
        List<PersonStatus> statuses = personStatuses(today);

        List<ApiCall> calls = apiCallRepository.findTop50ByChannelOrderByCalledAtDesc(CallChannel.ACTIVITIES_UPLOAD);
        LastUpload lastUpload = calls.isEmpty() ? null : toLastUpload(calls.get(0));

        List<PersonStatus> toFix = statuses.stream()
                .filter(PersonStatus::hasSomethingToFix)
                .sorted(Comparator.comparing((PersonStatus p) -> p.nextGapDeadline() == null ? LocalDate.MAX : p.nextGapDeadline())
                        .thenComparing(PersonStatus::displayName))
                .toList();

        Totals totals = new Totals(
                statuses.size(),
                (int) statuses.stream().filter(p -> !p.gapDates().isEmpty()).count(),
                statuses.stream().mapToInt(p -> p.gapDates().size()).sum(),
                (int) statuses.stream().filter(p -> !p.rejectedDates().isEmpty()).count(),
                statuses.stream().mapToInt(p -> p.rejectedDates().size()).sum(),
                statuses.stream().mapToInt(p -> p.excludedDates().size()).sum());

        return new Dashboard(today, today, periods, quotaService.status(CallChannel.ACTIVITIES_UPLOAD), lastUpload,
                feedbackService.pendingSubmissionDates(), totals, toFix);
    }

    /** Estado de TODAS las personas activas (también las que están al día). */
    @Transactional(readOnly = true)
    public List<PersonStatus> personStatuses(LocalDate today) {
        PeriodSnapshotService.Result snapshot = snapshotService.buildFor(today, false);
        List<LocalDate> dates = policy.submittableActivityDates(today);
        LocalDate from = dates.isEmpty() ? today : dates.get(0);

        Map<String, List<LocalDate>> gapsByUid = snapshot.gaps().stream()
                .collect(Collectors.groupingBy(Gap::uid, TreeMap::new, Collectors.mapping(Gap::date, Collectors.toList())));
        Map<String, List<ValidationProblem>> problemsByUid = snapshot.problems().stream()
                .collect(Collectors.groupingBy(ValidationProblem::uid));
        Map<String, List<LocalDate>> rejectedByUid = activityDayRepository.findInRangeWithLines(from, today).stream()
                .filter(ActivityDay::hasRejections)
                .collect(Collectors.groupingBy(d -> d.getPerson().getUid(), Collectors.mapping(ActivityDay::getActivityDate, Collectors.toList())));
        Set<String> withTemplate = templateRepository.findAllOfActivePersonsWithLines().stream()
                .map(t -> t.getPerson().getUid()).collect(Collectors.toSet());

        List<PersonStatus> result = new ArrayList<>();
        for (Person p : personRepository.findByActiveTrue()) {
            List<LocalDate> gaps = gapsByUid.getOrDefault(p.getUid(), List.of()).stream().sorted().toList();
            List<ValidationProblem> problems = problemsByUid.getOrDefault(p.getUid(), List.of());
            List<LocalDate> excluded = problems.stream().map(ValidationProblem::date).distinct().sorted().toList();
            List<String> codes = problems.stream().map(ValidationProblem::code).distinct().sorted().toList();
            List<LocalDate> rejected = rejectedByUid.getOrDefault(p.getUid(), List.of()).stream().sorted().toList();
            LocalDate nextDeadline = gaps.stream().map(policy::lastSubmissionDate).min(Comparator.naturalOrder()).orElse(null);
            result.add(new PersonStatus(p.getId(), p.getUid(), p.getDisplayName(), p.getEmail(), withTemplate.contains(p.getUid()),
                    gaps, nextDeadline, rejected, excluded, codes));
        }
        return result;
    }

    private static LastUpload toLastUpload(ApiCall c) {
        return new LastUpload(c.getCalledAt(), c.getSubmissionDate(), c.isOk(), c.getDayCount(), c.getRowCount(), c.getErrorMessage());
    }
}
