package com.vendor.arconnector.web;

import com.vendor.arconnector.activities.DaySubmission;
import com.vendor.arconnector.activities.ModifiablePeriodPolicy;
import com.vendor.arconnector.activities.MonthViewBuilder;
import com.vendor.arconnector.domain.*;
import com.vendor.arconnector.repository.ActivityDayRepository;
import com.vendor.arconnector.repository.DefaultTemplateRepository;
import com.vendor.arconnector.repository.PersonRepository;
import com.vendor.arconnector.service.DayValidationService;
import com.vendor.arconnector.service.MonthViewService;
import com.vendor.arconnector.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Gestión de personas en perímetro, sus plantillas por defecto y sus días
 * explícitos (excepciones a la plantilla).
 *
 * Autorización: los managers pueden todo; un consultor solo puede ver y
 * editar los días de su propia persona ({@code @access.self(#id)}).
 */
@RestController
@RequestMapping("/api/persons")
@PreAuthorize("hasRole('MANAGER')")
public class PersonController {

    public record MonthViewDto(Long personId, int year, int month, List<MonthViewBuilder.DayView> days) {
    }

    private final PersonRepository personRepository;
    private final DefaultTemplateRepository templateRepository;
    private final ActivityDayRepository activityDayRepository;
    private final DayValidationService validationService;
    private final MonthViewService monthViewService;
    private final ModifiablePeriodPolicy periodPolicy = new ModifiablePeriodPolicy();
    private final Clock clock;

    public PersonController(PersonRepository personRepository, DefaultTemplateRepository templateRepository,
                            ActivityDayRepository activityDayRepository, DayValidationService validationService,
                            MonthViewService monthViewService, Clock clock) {
        this.personRepository = personRepository;
        this.templateRepository = templateRepository;
        this.activityDayRepository = activityDayRepository;
        this.validationService = validationService;
        this.monthViewService = monthViewService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ personas

    @GetMapping
    public List<PersonDto> list(@RequestParam(required = false) Boolean active) {
        return personRepository.findAllByOrderByDisplayNameAsc().stream()
                .filter(p -> active == null || p.isActive() == active)
                .map(PersonDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') or @access.self(#id)")
    public PersonDto get(@PathVariable Long id) {
        return PersonDto.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonDto create(@Valid @RequestBody PersonDto dto) {
        personRepository.findByUid(Person.normalizeUid(dto.uid())).ifPresent(p -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una persona con uid " + dto.uid());
        });
        Person p = new Person();
        dto.applyTo(p);
        return PersonDto.from(personRepository.save(p));
    }

    @PutMapping("/{id}")
    public PersonDto update(@PathVariable Long id, @Valid @RequestBody PersonDto dto) {
        Person p = find(id);
        personRepository.findByUid(Person.normalizeUid(dto.uid()))
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otra persona con uid " + dto.uid());
                });
        dto.applyTo(p);
        p.setUpdatedAt(Instant.now(clock));
        return PersonDto.from(personRepository.save(p));
    }

    /** Baja: la persona deja de estar en perímetro desde hoy (no se borra nada). */
    @DeleteMapping("/{id}")
    public PersonDto deactivate(@PathVariable Long id) {
        Person p = find(id);
        p.setActive(false);
        if (p.getEndDate() == null) {
            p.setEndDate(LocalDate.now(clock));
        }
        p.setUpdatedAt(Instant.now(clock));
        return PersonDto.from(personRepository.save(p));
    }

    // ------------------------------------------------------------------ plantillas

    @GetMapping("/{id}/templates")
    @PreAuthorize("hasRole('MANAGER') or @access.self(#id)")
    public List<TemplateDto> templates(@PathVariable Long id) {
        return templateRepository.findByPersonWithLines(find(id)).stream().map(TemplateDto::from).toList();
    }

    @PostMapping("/{id}/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TemplateDto createTemplate(@PathVariable Long id, @Valid @RequestBody TemplateDto dto) {
        Person person = find(id);
        if (dto.validTo() != null && dto.validTo().isBefore(dto.validFrom())) {
            throw new IllegalArgumentException("validTo no puede ser anterior a validFrom");
        }
        validationService.validate(DaySubmission.worked(person.getUid(), dto.validFrom(),
                dto.lines().stream().map(LineDto::toSubmission).toList()));

        // Una plantilla nueva cierra la anterior "sin fin" el día antes de empezar; cualquier otro solape se rechaza
        LocalDate newTo = dto.validTo() == null ? LocalDate.MAX : dto.validTo();
        for (DefaultTemplate existing : templateRepository.findByPersonWithLines(person)) {
            LocalDate exTo = existing.getValidTo() == null ? LocalDate.MAX : existing.getValidTo();
            boolean overlaps = !existing.getValidFrom().isAfter(newTo) && !dto.validFrom().isAfter(exTo);
            if (!overlaps) {
                continue;
            }
            if (existing.getValidTo() == null && existing.getValidFrom().isBefore(dto.validFrom())) {
                existing.setValidTo(dto.validFrom().minusDays(1));
                templateRepository.save(existing);
            } else {
                throw new IllegalArgumentException("Se solapa con la plantilla que va del " + existing.getValidFrom()
                        + (existing.getValidTo() == null ? " en adelante" : " al " + existing.getValidTo())
                        + ". Elimínala o ajusta las fechas.");
            }
        }

        DefaultTemplate t = new DefaultTemplate(person, dto.validFrom(), dto.validTo());
        for (LineDto l : dto.lines()) {
            t.addLine(l.taskTypeCode().trim(), LineDto.blankToNull(l.sdaProjectId()), LineDto.blankToNull(l.jiraIssueId()),
                    LineDto.blankToNull(l.nonSdaProject()), l.minutes());
        }
        return TemplateDto.from(templateRepository.save(t));
    }

    @DeleteMapping("/{id}/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable Long id, @PathVariable Long templateId) {
        Person person = find(id);
        DefaultTemplate t = templateRepository.findById(templateId)
                .filter(x -> x.getPerson().getId().equals(person.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantilla no encontrada: " + templateId));
        templateRepository.delete(t);
    }

    // ------------------------------------------------------------------ mes y días explícitos

    @GetMapping("/{id}/month")
    @PreAuthorize("hasRole('MANAGER') or @access.self(#id)")
    public MonthViewDto month(@PathVariable Long id, @RequestParam int year, @RequestParam int month) {
        Person person = find(id);
        return new MonthViewDto(person.getId(), year, month, monthViewService.month(person, YearMonth.of(year, month)));
    }

    @GetMapping("/{id}/days")
    @PreAuthorize("hasRole('MANAGER') or @access.self(#id)")
    public List<DayDto> days(@PathVariable Long id,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return activityDayRepository.findByPersonInRangeWithLines(find(id), from, to).stream().map(DayDto::from).toList();
    }

    /** Crea o sustituye el día completo de la persona (una excepción a su plantilla). */
    @PutMapping("/{id}/days/{date}")
    @PreAuthorize("hasRole('MANAGER') or @access.self(#id)")
    @Transactional
    public DayDto upsertDay(@PathVariable Long id,
                            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @Valid @RequestBody DayUpsertDto dto) {
        Person person = find(id);
        requireOpenPeriod(date);
        if (dto.kind() == DayKind.NON_WORKING && !dto.linesOrEmpty().isEmpty()) {
            throw new IllegalArgumentException("Un día no imputable no puede llevar líneas");
        }
        validationService.validate(new DaySubmission(person.getUid(), date, dto.kind(),
                dto.linesOrEmpty().stream().map(LineDto::toSubmission).toList()));

        ActivityDay day = activityDayRepository.findByPersonAndActivityDate(person, date)
                .orElseGet(() -> new ActivityDay(person, date, dto.kind(), DayOrigin.MANUAL));
        day.setKind(dto.kind());
        day.setOrigin(DayOrigin.MANUAL);
        day.setNote(dto.note());
        day.clearFeedback();
        day.clearLines();
        for (LineDto l : dto.linesOrEmpty()) {
            day.addLine(l.taskTypeCode().trim(), LineDto.blankToNull(l.sdaProjectId()), LineDto.blankToNull(l.jiraIssueId()),
                    LineDto.blankToNull(l.nonSdaProject()), l.minutes());
        }
        day.setUpdatedAt(Instant.now(clock));
        return DayDto.from(activityDayRepository.save(day));
    }

    /** Elimina el día explícito: vuelve a aplicar la plantilla por defecto (o queda como hueco). */
    @DeleteMapping("/{id}/days/{date}")
    @PreAuthorize("hasRole('MANAGER') or @access.self(#id)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteDay(@PathVariable Long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Person person = find(id);
        requireOpenPeriod(date);
        activityDayRepository.findByPersonAndActivityDate(person, date).ifPresent(activityDayRepository::delete);
    }

    /** BBVA ya no admitiría ese día: el último envío posible fue el 19 (1ª quincena) o el 4 del mes siguiente (2ª). */
    private void requireOpenPeriod(LocalDate date) {
        LocalDate last = periodPolicy.lastSubmissionDate(date);
        if (LocalDate.now(clock).isAfter(last)) {
            throw new IllegalArgumentException("El periodo de " + date + " está cerrado en Activity Report desde el " + last.plusDays(1));
        }
    }

    private Person find(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Persona no encontrada: " + id));
    }
}
