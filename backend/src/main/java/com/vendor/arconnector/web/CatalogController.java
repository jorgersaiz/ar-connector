package com.vendor.arconnector.web;

import com.vendor.arconnector.activities.TaskTree;
import com.vendor.arconnector.catalog.CatalogName;
import com.vendor.arconnector.domain.*;
import com.vendor.arconnector.repository.*;
import com.vendor.arconnector.service.CatalogSyncResult;
import com.vendor.arconnector.service.CatalogSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Estado de los catálogos y sincronización manual (managers), y lectura de
 * los catálogos para los selectores del panel (cualquier usuario).
 */
@RestController
@RequestMapping("/api/catalogs")
public class CatalogController {

    public record CatalogStatusDto(String catalog, Instant lastAttemptAt, Instant lastSuccessAt,
                                   Integer rowCount, String lastError) {
    }

    /** Nodo del árbol para el selector, con las exigencias ya heredadas del camino. */
    public record TaskTypeDto(String code, String parentCode, String path, String descriptionEs, boolean canBeSelected,
                              boolean enabled, boolean requiresSdaProject, boolean requiresJiraIssue, boolean requiresNonSdaProject) {
    }

    public record SdaProjectDto(String sdaProjectId, String description, String status, boolean evolutionarySda, LocalDate disabledDate) {
    }

    public record JiraIssueDto(String jiraIssueId, String description, String sdaProjectId, String status, String type) {
    }

    public record NonSdaProjectDto(String description, LocalDate disabledDate) {
    }

    private final CatalogSyncService syncService;
    private final CatalogSyncStatusRepository statusRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final SdaProjectRepository sdaProjectRepository;
    private final JiraIssueRepository jiraIssueRepository;
    private final NonSdaProjectRepository nonSdaProjectRepository;
    private final Clock clock;

    public CatalogController(CatalogSyncService syncService, CatalogSyncStatusRepository statusRepository,
                             TaskTypeRepository taskTypeRepository, SdaProjectRepository sdaProjectRepository,
                             JiraIssueRepository jiraIssueRepository, NonSdaProjectRepository nonSdaProjectRepository,
                             Clock clock) {
        this.syncService = syncService;
        this.statusRepository = statusRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.sdaProjectRepository = sdaProjectRepository;
        this.jiraIssueRepository = jiraIssueRepository;
        this.nonSdaProjectRepository = nonSdaProjectRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ estado y sincronización

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public List<CatalogStatusDto> status() {
        Map<String, CatalogSyncStatus> byName = statusRepository.findAll().stream()
                .collect(Collectors.toMap(CatalogSyncStatus::getCatalogName, Function.identity()));
        return Arrays.stream(CatalogName.values())
                .map(c -> {
                    CatalogSyncStatus s = byName.get(c.apiName());
                    return s == null
                            ? new CatalogStatusDto(c.apiName(), null, null, null, null)
                            : new CatalogStatusDto(c.apiName(), s.getLastAttemptAt(), s.getLastSuccessAt(), s.getRowCount(), s.getLastError());
                })
                .toList();
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('MANAGER')")
    public List<CatalogSyncResult> syncAll() {
        return syncService.syncAll();
    }

    @PostMapping("/{catalog}/sync")
    @PreAuthorize("hasRole('MANAGER')")
    public CatalogSyncResult syncOne(@PathVariable String catalog) {
        CatalogName name = CatalogName.fromApiName(catalog)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catálogo desconocido: " + catalog));
        return syncService.sync(name);
    }

    // ------------------------------------------------------------------ selectores del panel

    @GetMapping("/task-types")
    public List<TaskTypeDto> taskTypes() {
        List<TaskType> all = taskTypeRepository.findAll();
        Map<String, TaskType> byCode = all.stream().collect(Collectors.toMap(TaskType::getTaskTypeId, Function.identity()));
        TaskTree tree = TaskTree.fromCatalog(all);
        LocalDate today = LocalDate.now(clock);

        return all.stream()
                .sorted(Comparator.comparing(TaskType::getTaskTypeId))
                .map(t -> {
                    String path;
                    TaskTree.Requirements req;
                    try {
                        path = tree.pathTo(t.getTaskTypeId()).stream()
                                .map(c -> Optional.ofNullable(byCode.get(c)).map(TaskType::getDescriptionEs).orElse(c))
                                .collect(Collectors.joining(" / "));
                        req = tree.effectiveRequirementsFor(t.getTaskTypeId());
                    } catch (RuntimeException e) {
                        path = t.getTaskTypeId();
                        req = new TaskTree.Requirements(t.isRequiresSdaProject(), t.isRequiresJiraIssue(), t.isRequiresNonSdaProject(), false,
                                t.isRequiresEvolutionarySda(), t.isRequiresEvolutionaryJiraIssue(), t.isRequiresConceptualizationTime());
                    }
                    boolean enabled = t.getDisabledDate() == null || !today.isAfter(t.getDisabledDate());
                    return new TaskTypeDto(t.getTaskTypeId(), t.getTaskTypeParentId(), path, t.getDescriptionEs(),
                            t.isCanBeSelected(), enabled, req.sdaProject(), req.jiraIssue(), req.nonSdaProject());
                })
                .toList();
    }

    @GetMapping("/sda-projects")
    public List<SdaProjectDto> sdaProjects() {
        return sdaProjectRepository.findAll().stream()
                .sorted(Comparator.comparing(SdaProject::getSdaProjectId))
                .map(p -> new SdaProjectDto(p.getSdaProjectId(), p.getDescription(), p.getStatus(), p.isEvolutionarySda(), p.getDisabledDate()))
                .toList();
    }

    @GetMapping("/jira-issues")
    public List<JiraIssueDto> jiraIssues(@RequestParam(required = false) String sdaProjectId) {
        return jiraIssueRepository.findAll().stream()
                .filter(i -> sdaProjectId == null || sdaProjectId.isBlank() || sdaProjectId.equals(i.getSdaProjectId()))
                .sorted(Comparator.comparing(JiraIssue::getJiraIssueId))
                .map(i -> new JiraIssueDto(i.getJiraIssueId(), i.getDescription(), i.getSdaProjectId(), i.getStatus(), i.getType()))
                .toList();
    }

    @GetMapping("/non-sda-projects")
    public List<NonSdaProjectDto> nonSdaProjects() {
        return nonSdaProjectRepository.findAll().stream()
                .sorted(Comparator.comparing(NonSdaProject::getDescription))
                .map(p -> new NonSdaProjectDto(p.getDescription(), p.getDisabledDate()))
                .toList();
    }
}
