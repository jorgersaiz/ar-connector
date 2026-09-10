package com.vendor.arconnector.web;

import com.vendor.arconnector.activities.DaySubmission;
import com.vendor.arconnector.domain.*;
import com.vendor.arconnector.repository.ActivityDayRepository;
import com.vendor.arconnector.repository.DefaultTemplateRepository;
import com.vendor.arconnector.repository.PersonRepository;
import com.vendor.arconnector.service.DayValidationService;
import com.vendor.arconnector.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Gestión de personas en perímetro, sus plantillas por defecto y sus días
 * explícitos (excepciones a la plantilla).
 */
@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonRepository personRepository;
    private final DefaultTemplateRepository templateRepository;
    private final ActivityDayRepository activityDayRepository;
    private final DayValidationService validationService;
    private final Clock clock;

    public PersonController(PersonRepository personRepository, DefaultTemplateRepository templateRepository,
                            ActivityDayRepository activityDayRepository, DayValidationService validationService, Clock clock) {
        this.personRepository = personRepository;
        this.templateRepository = templateRepository;
        this.activityDayRepository = activityDayRepository;
        this.validationService = validationService;
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

    // ------------------------------------------------------------------ días explícitos

    @GetMapping("/{id}/days")
    public List<DayDto> days(@PathVariable Long id,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return activityDayRepository.findByPersonInRangeWithLines(find(id), from, to).stream().map(DayDto::from).toList();
    }

    /** Crea o sustituye el día completo de la persona (una excepción a su plantilla). */
    @PutMapping("/{id}/days/{date}")
    @Transactional
    public DayDto upsertDay(@PathVariable Long id,
                            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @Valid @RequestBody DayUpsertDto dto) {
        Person person = find(id);
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteDay(@PathVariable Long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Person person = find(id);
        activityDayRepository.findByPersonAndActivityDate(person, date).ifPresent(activityDayRepository::delete);
    }

    private Person find(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Persona no encontrada: " + id));
    }
}
