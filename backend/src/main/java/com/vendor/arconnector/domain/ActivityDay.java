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
 * Día de actividad de una persona. Es la unidad de envío a Activity Report:
 * cada carga sobreescribe el día completo de la persona, por lo que siempre
 * se envían todas sus líneas juntas.
 */
@Entity
@Table(name = "activity_day")
@Getter
@Setter
@NoArgsConstructor
public class ActivityDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private DayKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 16)
    private DayOrigin origin;

    @Column(name = "note")
    private String note;

    /** Fecha del último fichero en el que se envió este día (para casarlo con su feedback). */
    @Column(name = "last_sent_date")
    private LocalDate lastSentDate;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ActivityLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ActivityDay(Person person, LocalDate activityDate, DayKind kind, DayOrigin origin) {
        this.person = person;
        this.activityDate = activityDate;
        this.kind = kind;
        this.origin = origin;
    }

    public static ActivityDay nonWorking(Person person, LocalDate date, DayOrigin origin, String note) {
        ActivityDay day = new ActivityDay(person, date, DayKind.NON_WORKING, origin);
        day.setNote(note);
        return day;
    }

    public ActivityLine addLine(String taskTypeCode, String sdaProjectId, String jiraIssueId,
                                String nonSdaProject, int minutes) {
        ActivityLine line = new ActivityLine(this, taskTypeCode, sdaProjectId, jiraIssueId, nonSdaProject, minutes);
        line.setSortOrder(lines.size());
        lines.add(line);
        return line;
    }

    public int totalMinutes() {
        return lines.stream().mapToInt(ActivityLine::getMinutes).sum();
    }
}
