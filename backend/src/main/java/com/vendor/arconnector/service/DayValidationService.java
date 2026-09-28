package com.vendor.arconnector.service;

import com.vendor.arconnector.activities.ActivitiesFileBuilder;
import com.vendor.arconnector.activities.DaySubmission;
import com.vendor.arconnector.activities.ModifiablePeriodPolicy;
import com.vendor.arconnector.activities.TaskTree;
import com.vendor.arconnector.activities.ValidationProblem;
import com.vendor.arconnector.repository.TaskTypeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Valida el contenido de días y plantillas contra el árbol de actividades y
 * los catálogos sincronizados, con las mismas reglas que el generador del
 * fichero, para rechazar en el momento de introducirlos lo que BBVA
 * rechazaría después.
 */
@Service
public class DayValidationService {

    private final TaskTypeRepository taskTypeRepository;
    private final CatalogRulesService catalogRulesService;

    public DayValidationService(TaskTypeRepository taskTypeRepository, CatalogRulesService catalogRulesService) {
        this.taskTypeRepository = taskTypeRepository;
        this.catalogRulesService = catalogRulesService;
    }

    /** Lanza {@link DayValidationException} si algún día tiene problemas de contenido. */
    public void validate(List<DaySubmission> days) {
        TaskTree tree = TaskTree.fromCatalog(taskTypeRepository.findAll());
        if (tree.isEmpty()) {
            throw new IllegalStateException("El árbol de actividades está vacío: sincroniza el catálogo task_types antes de introducir imputaciones");
        }
        ActivitiesFileBuilder builder = new ActivitiesFileBuilder(tree, new ModifiablePeriodPolicy(), catalogRulesService.loadFor(days));
        List<ValidationProblem> problems = new ArrayList<>();
        for (DaySubmission day : days) {
            problems.addAll(builder.validateContent(day));
        }
        if (!problems.isEmpty()) {
            throw new DayValidationException(problems);
        }
    }

    public void validate(DaySubmission day) {
        validate(List.of(day));
    }
}
