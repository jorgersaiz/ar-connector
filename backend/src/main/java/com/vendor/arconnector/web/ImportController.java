package com.vendor.arconnector.web;

import com.vendor.arconnector.service.DayImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Importación masiva de días desde CSV (manager). */
@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasRole('MANAGER')")
public class ImportController {

    private final DayImportService importService;

    public ImportController(DayImportService importService) {
        this.importService = importService;
    }

    /**
     * Sube un CSV con el formato
     * {@code uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note}.
     * Cada día del fichero sustituye por completo el que hubiera.
     */
    @PostMapping("/days")
    public DayImportService.Summary importDays(@RequestParam("file") MultipartFile file) {
        try {
            return importService.importCsv(file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
