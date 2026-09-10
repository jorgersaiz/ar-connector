package com.vendor.arconnector.web;

import com.vendor.arconnector.domain.Holiday;
import com.vendor.arconnector.repository.HolidayRepository;
import com.vendor.arconnector.web.dto.HolidayDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/** Calendario laboral: festivos por geografía (NATIONAL no se envía, LOCAL se envía como día no imputable). */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayRepository repository;
    private final Clock clock;

    public HolidayController(HolidayRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @GetMapping
    public List<HolidayDto> list(@RequestParam(required = false) String geography,
                                 @RequestParam(required = false) Integer year) {
        int y = year == null ? Year.now(clock).getValue() : year;
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);
        List<Holiday> holidays = geography == null
                ? repository.findByDateBetweenOrderByDateAsc(from, to)
                : repository.findByGeographyAndDateBetweenOrderByDateAsc(geography.toUpperCase(), from, to);
        return holidays.stream().map(HolidayDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayDto create(@Valid @RequestBody HolidayDto dto) {
        Holiday h = new Holiday(dto.geography().trim().toUpperCase(), dto.date(), dto.description().trim(), dto.scope());
        return HolidayDto.from(repository.save(h));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Festivo no encontrado: " + id);
        }
        repository.deleteById(id);
    }
}
