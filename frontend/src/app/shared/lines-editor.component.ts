import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormArray, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, Subscription, catchError, debounceTime, of, switchMap } from 'rxjs';
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

/** Estado de una fila del editor (grupo de formulario + resultados de búsqueda de sus selectores). */
export class LineRow {
  readonly group: FormGroup<LineForm>;
  filtered: TaskType[] = [];
  sdaOptions: SdaProject[] = [];
  jiraOptions: JiraIssue[] = [];
  nonSdaOptions: NonSdaProject[] = [];
  searchingSda = false;
  searchingJira = false;
  readonly sdaSearch = new Subject<string>();
  readonly jiraSearch = new Subject<string>();
  subscriptions: Subscription[] = [];

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

function filled(v: string | null): boolean {
  return !!v && v.trim().length > 0;
}

function lineValidator(control: AbstractControl): ValidationErrors | null {
  const g = control as FormGroup<LineForm>;
  const tt = g.controls.taskType.value;
  if (!tt || typeof tt !== 'object') return { taskTypeRequired: true };
  const errors: ValidationErrors = {};
  if (tt.requiresSdaProject && !filled(g.controls.sdaProjectId.value)) errors['sdaRequired'] = true;
  if (tt.jiraIssueRequired && !filled(g.controls.jiraIssueId.value)) errors['jiraRequired'] = true;
  if (tt.requiresNonSdaProject && !filled(g.controls.nonSdaProject.value)) errors['nonSdaRequired'] = true;
  return Object.keys(errors).length ? errors : null;
}

/**
 * Editor de líneas de imputación (compartido por el diálogo de día y el de plantilla).
 * El padre lee `value()` y `valid` al guardar.
 *
 * Los selectores de proyecto SDA e issue de Jira buscan en el servidor mientras
 * se escribe, porque los catálogos reales tienen miles de entradas. El valor
 * que se guarda es el código; el servidor valida que exista y sea imputable.
 */
@Component({
  selector: 'app-lines-editor',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatAutocompleteModule,
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
              <mat-label>Proyecto SDA{{ row.taskType?.requiresEvolutionarySda ? ' (de evolutivo)' : '' }}</mat-label>
              <input matInput formControlName="sdaProjectId" [matAutocomplete]="sdaAuto" placeholder="Escribe el código o parte del nombre"
                     (input)="searchSda(row)" (focus)="searchSda(row)" />
              <mat-autocomplete #sdaAuto (optionSelected)="onSdaChanged(row)">
                @if (row.searchingSda) { <mat-option disabled>Buscando…</mat-option> }
                @for (p of row.sdaOptions; track p.sdaProjectId) {
                  <mat-option [value]="p.sdaProjectId">
                    <span>{{ p.sdaProjectId }}</span>
                    <span class="muted small"> · {{ p.description }} · {{ p.status }}</span>
                  </mat-option>
                }
                @if (!row.searchingSda && row.sdaOptions.length === 0) { <mat-option disabled>Sin resultados</mat-option> }
              </mat-autocomplete>
              @if (row.sdaOptions.length >= 50) { <mat-hint>Se muestran 50; escribe más para acotar</mat-hint> }
            </mat-form-field>
          }
          @if (row.taskType?.jiraIssueExpected) {
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Issue Jira{{ row.taskType?.jiraIssueRequired ? '' : ' (opcional)' }}</mat-label>
              <input matInput formControlName="jiraIssueId" [matAutocomplete]="jiraAuto" placeholder="Escribe la clave o parte del título"
                     (input)="searchJira(row)" (focus)="searchJira(row)" />
              <mat-autocomplete #jiraAuto>
                @if (row.searchingJira) { <mat-option disabled>Buscando…</mat-option> }
                @for (j of row.jiraOptions; track j.jiraIssueId + '|' + j.sdaProjectId) {
                  <mat-option [value]="j.jiraIssueId">
                    <span>{{ j.jiraIssueId }}</span>
                    <span class="muted small"> · {{ j.description }} · {{ j.status }}</span>
                  </mat-option>
                }
                @if (!row.searchingJira && row.jiraOptions.length === 0) {
                  <mat-option disabled>{{ jiraEmptyText(row) }}</mat-option>
                }
              </mat-autocomplete>
              <mat-hint>{{ jiraHint(row) }}</mat-hint>
            </mat-form-field>
          }
          @if (row.taskType?.requiresNonSdaProject) {
            <mat-form-field subscriptSizing="dynamic">
              <mat-label>Proyecto no SDA</mat-label>
              <input matInput formControlName="nonSdaProject" [matAutocomplete]="nonSdaAuto" placeholder="Escribe parte del nombre"
                     (input)="filterNonSda(row)" (focus)="filterNonSda(row)" />
              <mat-autocomplete #nonSdaAuto>
                @for (p of row.nonSdaOptions; track p.description) {
                  <mat-option [value]="p.description">{{ p.description }}</mat-option>
                }
                @if (row.nonSdaOptions.length === 0) { <mat-option disabled>Sin resultados</mat-option> }
              </mat-autocomplete>
            </mat-form-field>
          }
          @if (row.taskType && !row.taskType.requiresSdaProject && !row.taskType.jiraIssueExpected && !row.taskType.requiresNonSdaProject) {
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
    /* tarea y proyecto a lo ancho (los nombres reales son largos); minutos y papelera a la derecha */
    .line-row {
      display: grid; grid-template-columns: minmax(0, 1fr) 110px 40px; gap: 14px 12px; align-items: start;
      margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid rgba(0,0,0,.08);
    }
    .line-row mat-form-field { width: 100%; }
    .deps { grid-column: 1; grid-row: 2; display: flex; flex-direction: column; gap: 8px; }
    .deps:empty { display: none; }
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
  readonly nonSdaProjects = signal<NonSdaProject[]>([]);
  readonly catalogReady = signal(false);
  readonly total = signal(0);

  ngOnInit(): void {
    for (const l of this.initial) this.push(new LineRow(l));
    this.form.valueChanges.subscribe(() => this.recomputeTotal());
    this.recomputeTotal();

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
            if (tt) row.group.updateValueAndValidity();
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
      const clean = (v: string | null) => (v && v.trim() ? v.trim() : null);
      return {
        taskTypeCode: tt?.code ?? String(c.taskType.value ?? ''),
        sdaProjectId: tt?.requiresSdaProject ? clean(c.sdaProjectId.value) : null,
        jiraIssueId: tt?.jiraIssueExpected ? clean(c.jiraIssueId.value) : null,
        nonSdaProject: tt?.requiresNonSdaProject ? clean(c.nonSdaProject.value) : null,
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
    this.rows[i].subscriptions.forEach((s) => s.unsubscribe());
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

  searchSda(row: LineRow): void {
    row.searchingSda = true;
    row.sdaSearch.next(row.group.controls.sdaProjectId.value ?? '');
  }

  searchJira(row: LineRow): void {
    const tt = row.taskType;
    if (tt?.requiresSdaProject && !filled(row.group.controls.sdaProjectId.value)) {
      row.jiraOptions = [];
      row.searchingJira = false;
      return;
    }
    row.searchingJira = true;
    row.jiraSearch.next(row.group.controls.jiraIssueId.value ?? '');
  }

  filterNonSda(row: LineRow): void {
    const q = (row.group.controls.nonSdaProject.value ?? '').toLowerCase().trim();
    const all = this.nonSdaProjects();
    row.nonSdaOptions = (q ? all.filter((p) => p.description.toLowerCase().includes(q)) : all).slice(0, 50);
  }

  jiraEmptyText(row: LineRow): string {
    if (row.taskType?.requiresSdaProject && !filled(row.group.controls.sdaProjectId.value)) return 'Indica antes el proyecto SDA';
    return 'Sin resultados';
  }

  jiraHint(row: LineRow): string {
    const tt = row.taskType;
    if (!tt) return '';
    if (tt.requiresConceptualizationTime) return 'Tiene que ser una issue de tipo Conceptualization Time';
    if (!tt.requiresSdaProject) return 'Features sin proyecto SDA';
    return 'Issues del proyecto SDA indicado';
  }

  onTaskTypeSelected(row: LineRow, ev: MatAutocompleteSelectedEvent): void {
    const tt = ev.option.value as TaskType;
    const c = row.group.controls;
    if (!tt.requiresSdaProject) c.sdaProjectId.setValue(null);
    if (!tt.jiraIssueExpected) c.jiraIssueId.setValue(null);
    if (!tt.requiresNonSdaProject) c.nonSdaProject.setValue(null);
    row.sdaOptions = [];
    row.jiraOptions = [];
    row.group.updateValueAndValidity();
  }

  onSdaChanged(row: LineRow): void {
    row.group.controls.jiraIssueId.setValue(null);
    row.jiraOptions = [];
  }

  private push(row: LineRow): void {
    row.subscriptions.push(
      row.sdaSearch
        .pipe(
          debounceTime(250),
          // un fallo puntual de la búsqueda no debe dejar el selector muerto
          switchMap((q) => this.catalogs.searchSdaProjects(q, row.taskType?.requiresEvolutionarySda ?? null)
            .pipe(catchError(() => of([] as SdaProject[])))),
        )
        .subscribe({
          next: (list) => { row.sdaOptions = list; row.searchingSda = false; },
          error: () => { row.sdaOptions = []; row.searchingSda = false; },
        }),
      row.jiraSearch
        .pipe(
          debounceTime(250),
          switchMap((q) => {
            const requiresSda = row.taskType?.requiresSdaProject ?? false;
            const sda = requiresSda ? (row.group.controls.sdaProjectId.value ?? '').trim() : null;
            return this.catalogs.searchJiraIssues(q, sda, !requiresSda).pipe(catchError(() => of([] as JiraIssue[])));
          }),
        )
        .subscribe({
          next: (list) => { row.jiraOptions = list; row.searchingJira = false; },
          error: () => { row.jiraOptions = []; row.searchingJira = false; },
        }),
    );
    this.rows.push(row);
    this.form.push(row.group);
  }

  private recomputeTotal(): void {
    this.total.set(this.rows.reduce((acc, r) => acc + r.minutes, 0));
  }
}
