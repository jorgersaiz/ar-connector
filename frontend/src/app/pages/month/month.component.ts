import { Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CatalogService } from '../../core/catalog.service';
import { NotifyService } from '../../core/notify.service';
import { DayView, MonthView, Person, TaskType } from '../../core/models';
import { todayIso, weekdayShort } from '../../core/dates';
import { confirm } from '../../shared/confirm-dialog.component';
import { HoursPipe, LineLabelPipe } from '../../shared/line-label.pipe';
import { DayEditDialogComponent, DayEditData } from './day-edit-dialog.component';

interface DayChip {
  label: string;
  cls: string;
}

const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

@Component({
  selector: 'app-month',
  standalone: true,
  imports: [
    DatePipe, RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule,
    MatDialogModule, MatCardModule, HoursPipe, LineLabelPipe,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>
          @if (person(); as p) {
            Mes de {{ p.displayName }} <span class="muted small">({{ p.uid }})</span>
          } @else {
            Mi mes
          }
        </h1>
        <span class="spacer"></span>
        @if (isManagerView()) {
          <a mat-stroked-button [routerLink]="['/personas', personId()]"><mat-icon>person</mat-icon> Ficha</a>
        }
      </div>

      <div class="toolbar-row">
        <button mat-icon-button (click)="shift(-1)" aria-label="Mes anterior"><mat-icon>chevron_left</mat-icon></button>
        <strong class="month-title">{{ monthTitle() }}</strong>
        <button mat-icon-button (click)="shift(1)" aria-label="Mes siguiente"><mat-icon>chevron_right</mat-icon></button>
        <button mat-button (click)="goToday()">Hoy</button>
        <span class="spacer"></span>
        <span class="muted">Total del mes: <strong>{{ monthTotal() | hours }}</strong></span>
        <button mat-icon-button (click)="load()" matTooltip="Recargar" aria-label="Recargar"><mat-icon>refresh</mat-icon></button>
      </div>

      <p class="hint">
        Los días sin excepción se imputan con tu plantilla. Cada día tiene una fecha límite de envío (pásate por la fecha para verla);
        pasada esa fecha el día queda bloqueado. Los días marcados como <span class="chip-day gap">Hueco</span> son laborables pasados sin nada que enviar.
      </p>

      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
      @if (error(); as e) { <div class="errors-box">{{ e }}</div> }

      @if (view(); as v) {
        <div class="table-wrap">
          <table mat-table [dataSource]="v.days">
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef>Día</th>
              <td mat-cell *matCellDef="let d" class="nowrap" [matTooltip]="dateTooltip(d)">
                {{ d.date | date: 'dd/MM' }} <span class="muted">{{ weekday(d.date) }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="chip">
              <th mat-header-cell *matHeaderCellDef>Estado</th>
              <td mat-cell *matCellDef="let d">
                <span [class]="'chip-day ' + chip(d).cls">{{ chip(d).label }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="lines">
              <th mat-header-cell *matHeaderCellDef>Líneas</th>
              <td mat-cell *matCellDef="let d">
                <div class="lines-list">
                  @for (l of d.lines; track $index) {
                    <div class="line" [class.warn-text]="l.rejectReason" [matTooltip]="l | lineLabel: taskTypes()">
                      {{ l | lineLabel: taskTypes() }}
                      @if (l.rejectReason) { <span class="mono">[{{ l.rejectReason }}]</span> }
                    </div>
                  }
                  @if (d.lines.length === 0 && d.kind === 'NON_WORKING') {
                    <span class="muted">Día no imputable</span>
                  }
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="total">
              <th mat-header-cell *matHeaderCellDef>Horas</th>
              <td mat-cell *matCellDef="let d" class="nowrap">{{ d.totalMinutes ? (d.totalMinutes | hours) : '' }}</td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Envío</th>
              <td mat-cell *matCellDef="let d" class="small">
                @if (d.lastSentDate) { <div>Enviado {{ d.lastSentDate | date: 'dd/MM' }}</div> }
                @if (d.hasRejections) {
                  <div class="warn-text" [matTooltip]="rejectTooltip(d)">Rechazado {{ rejectCode(d) }}</div>
                }
                @if (d.locked) { <div class="muted"><mat-icon inline>lock</mat-icon> Cerrado</div> }
                @if (d.note) { <div class="muted" [matTooltip]="d.note">{{ d.note }}</div> }
              </td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let d" class="nowrap">
                <button mat-button color="primary" (click)="edit(d)" [disabled]="!canEdit(d)"
                        [matTooltip]="d.locked ? 'Periodo cerrado' : ''">Editar</button>
                @if (d.source === 'EXPLICIT' && !d.locked) {
                  <button mat-button (click)="revert(d)" matTooltip="Elimina la excepción y vuelve a aplicar la plantilla">Volver a plantilla</button>
                }
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let d; columns: columns" [class]="rowClass(d)"></tr>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .month-title { min-width: 170px; text-align: center; text-transform: capitalize; }
    td.mat-mdc-cell { vertical-align: top; padding-top: 6px; padding-bottom: 6px; }
    mat-icon[inline] { font-size: 14px; vertical-align: middle; }
  `],
})
export class MonthComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly catalogs = inject(CatalogService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  /** Id de persona de la ruta /personas/:id/mes (vista de gestor). En /mi-mes es undefined. */
  readonly id = input<string>();

  readonly columns = ['date', 'chip', 'lines', 'total', 'status', 'actions'];
  readonly year = signal(new Date().getFullYear());
  readonly month = signal(new Date().getMonth() + 1);
  readonly view = signal<MonthView | null>(null);
  readonly person = signal<Person | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly taskTypes = signal<Map<string, TaskType> | null>(null);
  readonly today = todayIso();

  readonly isManagerView = computed(() => !!this.id());
  readonly personId = computed(() => (this.id() ? Number(this.id()) : this.auth.personId()));
  readonly monthTitle = computed(() => `${MONTHS[this.month() - 1]} ${this.year()}`);
  readonly monthTotal = computed(() => (this.view()?.days ?? []).reduce((a, d) => a + (d.totalMinutes ?? 0), 0));

  constructor() {
    effect(() => {
      // Recarga al cambiar persona o mes.
      this.personId(); this.year(); this.month();
      untracked(() => this.load());
    }, { allowSignalWrites: true });
    effect(() => {
      const id = this.id();
      if (!id) { this.person.set(null); return; }
      untracked(() => this.api.person(Number(id)).subscribe({ next: (p) => this.person.set(p), error: (e: unknown) => this.notify.error(e) }));
    }, { allowSignalWrites: true });
  }

  ngOnInit(): void {
    this.catalogs.taskTypes().subscribe({
      next: (list) => this.taskTypes.set(new Map(list.map((t) => [t.code, t]))),
      error: () => this.taskTypes.set(new Map()),
    });
  }

  load(): void {
    const id = this.personId();
    if (id == null) {
      this.error.set('Tu usuario no tiene una persona asociada. Pide a un gestor que la vincule.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.month(id, this.year(), this.month()).subscribe({
      next: (v) => { this.view.set(v); this.loading.set(false); },
      error: (err: unknown) => {
        this.loading.set(false);
        this.view.set(null);
        this.notify.error(err);
      },
    });
  }

  shift(delta: number): void {
    let m = this.month() + delta;
    let y = this.year();
    if (m < 1) { m = 12; y--; }
    if (m > 12) { m = 1; y++; }
    this.year.set(y);
    this.month.set(m);
  }

  goToday(): void {
    const now = new Date();
    this.year.set(now.getFullYear());
    this.month.set(now.getMonth() + 1);
  }

  weekday(iso: string): string {
    return weekdayShort(iso);
  }

  chip(d: DayView): DayChip {
    if (d.classification === 'WEEKEND') return { label: 'Fin de semana', cls: 'weekend' };
    if (d.classification === 'NATIONAL_HOLIDAY') return { label: 'Festivo', cls: 'holiday' };
    if (d.classification === 'LOCAL_HOLIDAY') return { label: 'Festivo local', cls: 'holiday' };
    if (d.gap) return { label: 'Hueco', cls: 'gap' };
    if (d.source === 'EXPLICIT') {
      if (d.kind === 'NON_WORKING') return { label: 'No imputable', cls: 'nonworking' };
      if (d.origin === 'CSV_IMPORT') return { label: 'Importado', cls: 'imported' };
      if (d.origin === 'TEMPLATE') return { label: 'Plantilla', cls: 'template' };
      return { label: 'Manual', cls: 'manual' };
    }
    if (d.source === 'TEMPLATE') return { label: 'Plantilla', cls: 'template' };
    if (d.source === 'HOLIDAY') return { label: 'Festivo', cls: 'holiday' };
    if (d.future) return { label: 'Futuro sin plantilla', cls: 'future' };
    return { label: 'Sin imputación', cls: '' };
  }

  canEdit(d: DayView): boolean {
    return !d.locked && d.classification !== 'WEEKEND' && d.classification !== 'NATIONAL_HOLIDAY';
  }

  rowClass(d: DayView): string {
    const cls: string[] = [];
    if (d.gap) cls.push('row-gap');
    if (d.classification === 'WEEKEND') cls.push('row-weekend');
    if (d.classification === 'NATIONAL_HOLIDAY' || d.classification === 'LOCAL_HOLIDAY') cls.push('row-holiday');
    if (d.date === this.today) cls.push('row-today');
    return cls.join(' ');
  }

  dateTooltip(d: DayView): string {
    const parts: string[] = [];
    if (d.lastSubmissionDate) parts.push(`Fecha límite de envío: ${this.fmt(d.lastSubmissionDate)}`);
    if (d.lastFeedbackDate) parts.push(`Último feedback: ${this.fmt(d.lastFeedbackDate)}`);
    if (d.locked) parts.push('Periodo cerrado: no se puede editar');
    return parts.join('\n');
  }

  rejectCode(d: DayView): string {
    const codes = new Set<string>();
    if (d.rejectReason) codes.add(d.rejectReason);
    for (const l of d.lines) if (l.rejectReason) codes.add(l.rejectReason);
    return [...codes].join(', ');
  }

  rejectTooltip(d: DayView): string {
    const lines = d.lines.filter((l) => l.rejectReason).map((l) => `${l.taskTypeCode}: ${l.rejectReason}`);
    if (d.rejectReason) lines.unshift(`Día: ${d.rejectReason}`);
    return lines.join('\n');
  }

  edit(d: DayView): void {
    const id = this.personId();
    if (id == null) return;
    this.dialog
      .open<DayEditDialogComponent, DayEditData, boolean>(DayEditDialogComponent, { data: { personId: id, day: d }, width: '860px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.notify.ok('Día guardado');
          this.load();
        }
      });
  }

  revert(d: DayView): void {
    const id = this.personId();
    if (id == null) return;
    confirm(this.dialog, {
      title: 'Volver a plantilla',
      message: `Se eliminará la excepción del ${this.fmt(d.date)} y el día volverá a imputarse con la plantilla vigente.`,
      confirmLabel: 'Eliminar excepción',
      warn: true,
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.deleteDay(id, d.date).subscribe({
        next: () => { this.notify.ok('Excepción eliminada'); this.load(); },
        error: (err: unknown) => this.notify.error(err),
      });
    });
  }

  private fmt(iso: string): string {
    const [y, m, d] = iso.substring(0, 10).split('-');
    return `${d}/${m}/${y}`;
  }
}
