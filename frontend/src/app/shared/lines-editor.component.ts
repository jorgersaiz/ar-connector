import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CatalogService } from '../core/catalog.service';
import { JiraIssue, Line, NonSdaProject, SdaProject, TaskType } from '../core/models';
import { minutesToHours } from '../core/dates';

interface LineForm {
  taskType: FormControl<TaskType | string | null>;
  sdaProjectId: FormControl<string | null>;
  jiraIssueId: FormControl<string | null>;
  nonSdaProject: FormControl<string | null>;
  minutes: FormControl<number | null>;
}

/** Estado de una fila del editor (grupo de formulario + datos dependientes del tipo de tarea). */
export class LineRow {
  readonly group: FormGroup<LineForm>;
  filtered: TaskType[] = [];
  jiraIssues: JiraIssue[] = [];
  loadingJira = false;

  constructor(line?: Line) {
    this.group = new FormGroup<LineForm>(
      {
        taskType: new FormControl<TaskType | string | null>(line?.taskTypeCode ?? null),
        sdaProjectId: new FormControl<string | null>(line?.sdaProjectId ?? null),
        jiraIssueId: new FormControl<string | null>(line?.jiraIssueId ?? null),
        nonSdaProject: new FormControl<string | null>(line?.nonSdaProject ?? null),
        minutes: new FormControl<number | null>(line?.minutes ?? null, [Validators.required, Validators.min(1), Validators.max(1440)]),
      },
      { validators: lineValidator },
    );
  }

  get taskType(): TaskType | null {
    const v = this.group.controls.taskType.value;
    return v && typeof v === 'object' ? v : null;
  }

  get minutes(): number {
    return Number(this.group.controls.minutes.value) || 0;
  }
}

function lineValidator(control: AbstractControl): ValidationErrors | null {
  const g = control as FormGroup<LineForm>;
  const tt = g.controls.taskType.value;
  if (!tt || typeof tt !== 'object') return { taskTypeRequired: true };
  const errors: ValidationErrors = {};
  if (tt.requiresSdaProject && !g.controls.sdaProjectId.value) errors['sdaRequired'] = true;
  if (tt.requiresJiraIssue && !g.controls.jiraIssueId.value) errors['jiraRequired'] = true;
  if (tt.requiresNonSdaProject && !g.controls.nonSdaProject.value) errors['nonSdaRequired'] = true;
  return Object.keys(errors).length ? errors : null;
}

/**
 * Editor de líneas de imputación (compartido por el diálogo de día y el de plantilla).
 * El padre lee `value()` y `valid` al guardar.
 */
@Component({
  selector: 'app-lines-editor',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatAutocompleteModule,
    MatButtonModule, MatIconModule, MatTooltipModule, MatProgressSpinnerModule,
  ],
  template: `
    @if (!catalogReady()) {
      <div class="loading"><mat-spinner diameter="20" /> Cargando catálogos…</div>
    } @else if (taskTypes().length === 0) {
      <div class="errors-box">No hay tipos de tarea disponibles. Un gestor debe sincronizar los catálogos.</div>
    }

    @for (row of rows; track row; let i = $index) {
      <div class="line-row" [formGroup]="row.group">
        <mat-form-field subscriptSizing="dynamic">
          <mat-label>Tipo de tarea</mat-label>
          <input matInput formControlName="taskType" [matAutocomplete]="auto" (input)="filter(row)" (focus)="filter(row)" />
          <mat-autocomplete #auto [displayWith]="displayTaskType" (optionSelected)="onTaskTypeSelected(row, $event)">
            @for (tt of row.filtered; track tt.code) {
              <mat-option [value]="tt">
                <span>{{ tt.path }}</span>
                <span class="muted small"> · {{ tt.code }}</span>
              </mat-option>
            }
          </mat-autocomplete>
          @if (row.group.hasError('taskTypeRequired') && row.group.touched) {
            <mat-error>Selecciona un tipo de tarea de la lista</mat-error>
          }
          @if (row.taskType; as tt) {
            <mat-hint>{{ tt.code }}</mat-hint>
          }
        </mat-form-field>

        <div class="deps">
          @if (row.taskType?.requiresSdaProject) {
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Proyecto SDA</mat-label>
              <mat-select formControlName="sdaProjectId" (selectionChange)="onSdaChanged(row)">
                @for (p of sdaProjects(); track p.sdaProjectId) {
                  <mat-option [value]="p.sdaProjectId">{{ p.sdaProjectId }} · {{ p.description }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
          @if (row.taskType?.requiresJiraIssue) {
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Issue Jira</mat-label>
              <mat-select formControlName="jiraIssueId">
                @if (row.loadingJira) {
                  <mat-option disabled>Cargando…</mat-option>
                } @else if (!row.group.controls.sdaProjectId.value) {
                  <mat-option disabled>Selecciona antes el proyecto SDA</mat-option>
                } @else if (row.jiraIssues.length === 0) {
                  <mat-option disabled>Sin issues para el proyecto seleccionado</mat-option>
                }
                @for (j of row.jiraIssues; track j.jiraIssueId) {
                  <mat-option [value]="j.jiraIssueId">{{ j.jiraIssueId }} · {{ j.description }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
          @if (row.taskType?.requiresNonSdaProject) {
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Proyecto no SDA</mat-label>
              <mat-select formControlName="nonSdaProject">
                @for (p of nonSdaProjects(); track p.description) {
                  <mat-option [value]="p.description">{{ p.description }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
          @if (row.taskType && !row.taskType.requiresSdaProject && !row.taskType.requiresJiraIssue && !row.taskType.requiresNonSdaProject) {
            <span class="muted small no-deps">Sin proyecto asociado</span>
          }
        </div>

        <mat-form-field subscriptSizing="dynamic">
          <mat-label>Minutos</mat-label>
          <input matInput type="number" min="1" max="1440" formControlName="minutes" />
          <mat-hint>{{ hours(row.minutes) }}</mat-hint>
        </mat-form-field>

        <button mat-icon-button type="button" color="warn" (click)="remove(i)" matTooltip="Quitar línea" aria-label="Quitar línea">
          <mat-icon>delete</mat-icon>
        </button>
      </div>
    }

    <div class="footer">
      <button mat-stroked-button type="button" (click)="add()" [disabled]="!catalogReady()">
        <mat-icon>add</mat-icon> Añadir línea
      </button>
      <span class="total">Total: <strong>{{ hours(total()) }}</strong> ({{ total() }} min)</span>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .line-row { margin-bottom: 10px; }
    .deps { display: flex; flex-direction: column; gap: 8px; }
    .no-deps { padding-top: 14px; display: inline-block; }
    .footer { display: flex; align-items: center; gap: 16px; margin-top: 8px; }
    .total { margin-left: auto; }
    .loading { display: flex; align-items: center; gap: 8px; font-size: 13px; color: rgba(0,0,0,.6); margin-bottom: 8px; }
  `],
})
export class LinesEditorComponent implements OnInit {
  private readonly catalogs = inject(CatalogService);

  /** Líneas iniciales (edición). */
  @Input() initial: Line[] = [];

  rows: LineRow[] = [];
  readonly form = new FormArray<FormGroup<LineForm>>([]);
  readonly taskTypes = signal<TaskType[]>([]);
  readonly sdaProjects = signal<SdaProject[]>([]);
  readonly nonSdaProjects = signal<NonSdaProject[]>([]);
  readonly catalogReady = signal(false);
  readonly total = signal(0);

  ngOnInit(): void {
    for (const l of this.initial) this.push(new LineRow(l));
    this.form.valueChanges.subscribe(() => this.recomputeTotal());
    this.recomputeTotal();

    this.catalogs.sdaProjects().subscribe({ next: (l) => this.sdaProjects.set(l), error: () => this.sdaProjects.set([]) });
    this.catalogs.nonSdaProjects().subscribe({ next: (l) => this.nonSdaProjects.set(l), error: () => this.nonSdaProjects.set([]) });
    this.catalogs.selectableTaskTypes().subscribe({
      next: (list) => {
        this.taskTypes.set(list);
        this.catalogReady.set(true);
        // Resolver los códigos de las líneas iniciales a objetos TaskType.
        for (const row of this.rows) {
          const v = row.group.controls.taskType.value;
          if (typeof v === 'string') {
            const tt = list.find((t) => t.code === v);
            row.group.controls.taskType.setValue(tt ?? v);
            if (tt) this.afterTaskType(row, tt, true);
          }
          row.filtered = list;
        }
      },
      error: () => this.catalogReady.set(true),
    });
  }

  get valid(): boolean {
    return this.form.valid;
  }

  /** Marca todo como tocado para mostrar errores. */
  touch(): void {
    this.form.markAllAsTouched();
  }

  value(): Line[] {
    return this.rows.map((row) => {
      const tt = row.taskType;
      const c = row.group.controls;
      return {
        taskTypeCode: tt?.code ?? String(c.taskType.value ?? ''),
        sdaProjectId: tt?.requiresSdaProject ? c.sdaProjectId.value : null,
        jiraIssueId: tt?.requiresJiraIssue ? c.jiraIssueId.value : null,
        nonSdaProject: tt?.requiresNonSdaProject ? c.nonSdaProject.value : null,
        minutes: Number(c.minutes.value) || 0,
      };
    });
  }

  add(): void {
    const row = new LineRow();
    row.filtered = this.taskTypes();
    this.push(row);
  }

  remove(i: number): void {
    this.rows.splice(i, 1);
    this.form.removeAt(i);
  }

  hours(min: number): string {
    return minutesToHours(min);
  }

  displayTaskType = (tt: TaskType | string | null): string => (tt && typeof tt === 'object' ? tt.path : (tt ?? ''));

  filter(row: LineRow): void {
    const v = row.group.controls.taskType.value;
    const q = (typeof v === 'string' ? v : '').toLowerCase().trim();
    const all = this.taskTypes();
    row.filtered = q
      ? all.filter((t) => t.path.toLowerCase().includes(q) || t.code.toLowerCase().includes(q) || t.descriptionEs.toLowerCase().includes(q))
      : all;
  }

  onTaskTypeSelected(row: LineRow, ev: MatAutocompleteSelectedEvent): void {
    const tt = ev.option.value as TaskType;
    this.afterTaskType(row, tt, false);
  }

  onSdaChanged(row: LineRow): void {
    row.group.controls.jiraIssueId.setValue(null);
    this.loadJira(row);
  }

  private afterTaskType(row: LineRow, tt: TaskType, keepValues: boolean): void {
    const c = row.group.controls;
    if (!keepValues) {
      if (!tt.requiresSdaProject) c.sdaProjectId.setValue(null);
      if (!tt.requiresJiraIssue) c.jiraIssueId.setValue(null);
      if (!tt.requiresNonSdaProject) c.nonSdaProject.setValue(null);
    }
    if (tt.requiresJiraIssue) this.loadJira(row);
    row.group.updateValueAndValidity();
  }

  private loadJira(row: LineRow): void {
    const sda = row.group.controls.sdaProjectId.value;
    row.jiraIssues = [];
    if (!sda) return;
    row.loadingJira = true;
    this.catalogs.jiraIssuesOf(sda).subscribe({
      next: (list) => { row.jiraIssues = list; row.loadingJira = false; },
      error: () => { row.loadingJira = false; },
    });
  }

  private push(row: LineRow): void {
    this.rows.push(row);
    this.form.push(row.group);
  }

  private recomputeTotal(): void {
    this.total.set(this.rows.reduce((acc, r) => acc + r.minutes, 0));
  }
}
