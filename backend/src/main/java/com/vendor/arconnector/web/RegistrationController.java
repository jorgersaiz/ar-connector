package com.vendor.arconnector.web;

import com.vendor.arconnector.domain.ApiCall;
import com.vendor.arconnector.domain.CallChannel;
import com.vendor.arconnector.domain.Person;
import com.vendor.arconnector.domain.RegistrationStatus;
import com.vendor.arconnector.repository.ApiCallRepository;
import com.vendor.arconnector.repository.PersonRepository;
import com.vendor.arconnector.service.CallQuotaService;
import com.vendor.arconnector.service.UserRegistrationService;
import com.vendor.arconnector.web.dto.PersonDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Registro de usuarios nuevos en Activity Report (managers). */
@RestController
@RequestMapping("/api/registrations")
@PreAuthorize("hasRole('MANAGER')")
public class RegistrationController {

    public record Overview(List<PersonDto> persons, CallQuotaService.QuotaStatus quota,
                           UploadController.ApiCallDto lastUpload, List<LocalDate> pendingFeedbackDates) {
    }

    private final UserRegistrationService registrationService;
    private final PersonRepository personRepository;
    private final ApiCallRepository apiCallRepository;
    private final CallQuotaService quotaService;
    private final Clock clock;

    public RegistrationController(UserRegistrationService registrationService, PersonRepository personRepository,
                                  ApiCallRepository apiCallRepository, CallQuotaService quotaService, Clock clock) {
        this.registrationService = registrationService;
        this.personRepository = personRepository;
        this.apiCallRepository = apiCallRepository;
        this.quotaService = quotaService;
        this.clock = clock;
    }

    @GetMapping
    public Overview overview() {
        List<ApiCall> calls = apiCallRepository.findTop50ByChannelOrderByCalledAtDesc(CallChannel.USER_REGISTRATION_UPLOAD);
        return new Overview(
                personRepository.findByRegistrationStatusNotOrderByDisplayNameAsc(RegistrationStatus.NOT_REQUIRED)
                        .stream().map(PersonDto::from).toList(),
                quotaService.status(CallChannel.USER_REGISTRATION_UPLOAD),
                calls.isEmpty() ? null : UploadController.ApiCallDto.from(calls.get(0)),
                registrationService.pendingFeedbackDates());
    }

    /** Envía ahora las personas pendientes de alta. */
    @PostMapping("/run")
    public UserRegistrationService.UploadResult run() {
        return registrationService.uploadNow();
    }

    @PostMapping("/feedback/sync")
    public List<UserRegistrationService.FeedbackResult> syncFeedback() {
        return registrationService.syncPendingFeedback();
    }

    /** Marca la persona para incluirla en el próximo fichero de registro. */
    @PostMapping("/persons/{id}/request")
    public PersonDto request(@PathVariable Long id) {
        Person p = find(id);
        RegistrationStatus s = p.getRegistrationStatus();
        if (s == RegistrationStatus.SENT || s == RegistrationStatus.REGISTERED) {
            throw new IllegalArgumentException("La persona ya está " + (s == RegistrationStatus.SENT ? "enviada, pendiente de feedback" : "registrada"));
        }
        p.setRegistrationStatus(RegistrationStatus.PENDING);
        p.setRegistrationReason(null);
        p.setUpdatedAt(Instant.now(clock));
        return PersonDto.from(personRepository.save(p));
    }

    /** Desiste del alta (solo si todavía no se ha enviado). */
    @PostMapping("/persons/{id}/cancel")
    public PersonDto cancel(@PathVariable Long id) {
        Person p = find(id);
        if (p.getRegistrationStatus() == RegistrationStatus.SENT) {
            throw new IllegalArgumentException("El alta ya se ha enviado; espera al feedback");
        }
        p.setRegistrationStatus(RegistrationStatus.NOT_REQUIRED);
        p.setRegistrationReason(null);
        p.setUpdatedAt(Instant.now(clock));
        return PersonDto.from(personRepository.save(p));
    }

    private Person find(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Persona no encontrada: " + id));
    }
}
