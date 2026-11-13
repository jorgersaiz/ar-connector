package com.vendor.arconnector.web;

import com.vendor.arconnector.service.DayImportService;
import com.vendor.arconnector.service.PersonImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Importación masiva desde CSV de días y de personas (manager). */
@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasRole('MANAGER')")
public class ImportController {

    private final DayImportService importService;
    private final PersonImportService personImportService;

    public ImportController(DayImportService importService, PersonImportService personImportService) {
        this.importService = importService;
        this.personImportService = personImportService;
    }

    /**
     * Sube un CSV con el formato
     * {@code uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note}.
     * Cada día del fichero sustituye por completo el que hubiera.
     */
    @PostMapping("/days")
    public DayImportService.Summary importDays(@RequestParam("file") MultipartFile file) {
        return importService.importCsv(bytes(file));
    }

    /**
     * Sube un CSV con el formato
     * {@code uid;display_name;email;start_date;end_date;geography;gps_code;needs_registration}.
     * Crea las personas nuevas y actualiza las que ya existen (mismo uid). Con
     * {@code dryRun=true} devuelve lo que haría sin guardar nada.
     */
    @PostMapping("/persons")
    public PersonImportService.Summary importPersons(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(defaultValue = "false") boolean dryRun) {
        return personImportService.importCsv(bytes(file), dryRun);
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
