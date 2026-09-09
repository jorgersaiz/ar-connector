package com.vendor.arconnector.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla por defecto de una persona: qué se imputa cada día laborable que
 * no tenga un {@link ActivityDay} explícito. Normalmente una sola línea con su
 * proyecto habitual y 480 minutos. La persona solo introduce excepciones.
 *
 * Una persona puede tener varias plantillas con rangos de validez distintos
 * (cambio de proyecto a partir de una fecha, etc.); para un día dado aplica
 * la que lo contenga. Los rangos no deberían solaparse.
 */
@Entity
@Table(name = "default_template")
@Getter
@Setter
@NoArgsConstructor
public class DefaultTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<DefaultTemplateLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public DefaultTemplate(Person person, LocalDate validFrom, LocalDate validTo) {
        this.person = person;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public DefaultTemplateLine addLine(String taskTypeCode, String sdaProjectId, String jiraIssueId,
                                       String nonSdaProject, int minutes) {
        DefaultTemplateLine line = new DefaultTemplateLine(this, taskTypeCode, sdaProjectId, jiraIssueId, nonSdaProject, minutes);
        line.setSortOrder(lines.size());
        lines.add(line);
        return line;
    }

    public boolean appliesOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    /** Materializa la plantilla como un día trabajado concreto. */
    public ActivityDay materialize(LocalDate date) {
        ActivityDay day = new ActivityDay(person, date, DayKind.WORKED, DayOrigin.TEMPLATE);
        for (DefaultTemplateLine l : lines) {
            day.addLine(l.getTaskTypeCode(), l.getSdaProjectId(), l.getJiraIssueId(), l.getNonSdaProject(), l.getMinutes());
        }
        return day;
    }
}
