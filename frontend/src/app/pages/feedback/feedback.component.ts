import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { FeedbackFile, FeedbackRow, FeedbackSyncResult, FeedbackSyncStatus } from '../../core/models';

const SYNC_LABEL: Record<FeedbackSyncStatus, string> = {
  APPLIED: 'Aplicado',
  NOT_AVAILABLE_YET: 'Aún no disponible',
  FAILED: 'Error',
};

@Component({
  selector: 'app-feedback',
  standalone: true,
  imports: [
    DatePipe, MatCardModule, MatButtonModule, MatIconModule, MatTableModule, MatTooltipModule,
    MatProgressBarModule, MatChipsModule, MatSlideToggleModule,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Rechazos y feedback</h1>
        <span class="spacer"></span>
        <button mat-flat-button color="primary" (click)="sync()" [disabled]="busy()"><mat-icon>sync</mat-icon> Sincronizar feedback</button>
      </div>
      @if (busy()) { <mat-progress-bar mode="indeterminate" /> }

      <p class="hint">
        BBVA publica un fichero de feedback por cada fecha de envío. Al sincronizar se descargan los pendientes y los rechazos se anotan en los días afectados.
      </p>

      @if (pending().length) {
        <div class="info-box">
          Fechas de envío con feedback pendiente:
          @for (d of pending(); track d) { <span class="chip-day holiday" style="margin-left: 6px">{{ d | date: 'dd/MM/yyyy' }}</span> }
        </div>
      } @else {
        <div class="muted small" style="margin-bottom: 12px">No hay feedback pendiente de descargar.</div>
      }

      @if (syncResults(); as results) {
        <mat-card class="card">
          <mat-card-header><mat-card-title>Resultado de la sincronización</mat-card-title></mat-card-header>
          <mat-card-content>
            @if (results.length === 0) { <span class="muted">Nada que sincronizar.</span> }
            <div class="table-wrap">
              <table mat-table [dataSource]="results">
                <ng-container matColumnDef="submissionDate"><th mat-header-cell *matHeaderCellDef>Fecha envío</th><td mat-cell *matCellDef="let r">{{ r.submissionDate | date: 'dd/MM/yyyy' }}</td></ng-container>
                <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Estado</th><td mat-cell *matCellDef="let r" [class]="r.status === 'FAILED' ? 'warn-text' : (r.status === 'APPLIED' ? 'ok-text' : 'muted')">{{ syncLabel(r.status) }}</td></ng-container>
                <ng-container matColumnDef="rowCount"><th mat-header-cell *matHeaderCellDef>Filas</th><td mat-cell *matCellDef="let r">{{ r.rowCount ?? '' }}</td></ng-container>
                <ng-container matColumnDef="rejectedCount"><th mat-header-cell *matHeaderCellDef>Rechazadas</th><td mat-cell *matCellDef="let r">{{ r.rejectedCount ?? '' }}</td></ng-container>
                <ng-container matColumnDef="matchedRows"><th mat-header-cell *matHeaderCellDef>Casadas</th><td mat-cell *matCellDef="let r">{{ r.matchedRows ?? '' }}</td></ng-container>
                <ng-container matColumnDef="unmatchedRows"><th mat-header-cell *matHeaderCellDef>Sin casar</th><td mat-cell *matCellDef="let r">{{ r.unmatchedRows ?? '' }}</td></ng-container>
                <ng-container matColumnDef="error"><th mat-header-cell *matHeaderCellDef>Error</th><td mat-cell *matCellDef="let r" class="warn-text small">{{ r.error ?? '' }}</td></ng-container>
                <tr mat-header-row *matHeaderRowDef="syncColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: syncColumns"></tr>
              </table>
            </div>
          </mat-card-content>
        </mat-card>
      }

      <h3>Ficheros de feedback descargados</h3>
      <div class="table-wrap">
        <table mat-table [dataSource]="files()">
          <ng-container matColumnDef="submissionDate"><th mat-header-cell *matHeaderCellDef>Fecha envío</th><td mat-cell *matCellDef="let f">{{ f.submissionDate | date: 'dd/MM/yyyy' }}</td></ng-container>
          <ng-container matColumnDef="downloadedAt"><th mat-header-cell *matHeaderCellDef>Descargado</th><td mat-cell *matCellDef="let f">{{ f.downloadedAt | date: 'dd/MM/yyyy HH:mm' }}</td></ng-container>
          <ng-container matColumnDef="rowCount"><th mat-header-cell *matHeaderCellDef>Filas</th><td mat-cell *matCellDef="let f">{{ f.rowCount }}</td></ng-container>
          <ng-container matColumnDef="rejectedCount"><th mat-header-cell *matHeaderCellDef>Rechazadas</th><td mat-cell *matCellDef="let f" [class.warn-text]="f.rejectedCount > 0">{{ f.rejectedCount }}</td></ng-container>
          <ng-container matColumnDef="matchedCount"><th mat-header-cell *matHeaderCellDef>Casadas</th><td mat-cell *matCellDef="let f">{{ f.matchedCount }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="fileColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: fileColumns" (click)="select(row)" class="clickable" [class.selected]="row.submissionDate === selectedDate()"></tr>
          <tr class="mat-row" *matNoDataRow><td class="mat-cell muted" [attr.colspan]="fileColumns.length" style="padding: 16px">Sin ficheros de feedback</td></tr>
        </table>
      </div>

      @if (selectedDate(); as date) {
        <div class="page-header" style="margin-top: 20px">
          <h3 style="margin: 0">Filas del feedback del {{ date | date: 'dd/MM/yyyy' }}</h3>
          <span class="spacer"></span>
          <mat-slide-toggle [checked]="onlyRejected()" (change)="toggleOnlyRejected($event.checked)">Solo rechazadas</mat-slide-toggle>
        </div>
        @if (loadingRows()) { <mat-progress-bar mode="indeterminate" /> }
        <div class="table-wrap">
          <table mat-table [dataSource]="rows()">
            <ng-container matColumnDef="activityDate"><th mat-header-cell *matHeaderCellDef>Día</th><td mat-cell *matCellDef="let r" class="nowrap">{{ r.activityDate | date: 'dd/MM/yyyy' }}</td></ng-container>
            <ng-container matColumnDef="personId"><th mat-header-cell *matHeaderCellDef>Person ID</th><td mat-cell *matCellDef="let r" class="mono" [matTooltip]="r.personId">{{ shorten(r.personId) }}</td></ng-container>
            <ng-container matColumnDef="taskTypeCode"><th mat-header-cell *matHeaderCellDef>Tipo tarea</th><td mat-cell *matCellDef="let r" class="mono">{{ r.taskTypeCode ?? '' }}</td></ng-container>
            <ng-container matColumnDef="sdaProjectId"><th mat-header-cell *matHeaderCellDef>SDA</th><td mat-cell *matCellDef="let r" class="mono">{{ r.sdaProjectId ?? '' }}</td></ng-container>
            <ng-container matColumnDef="jiraIssueId"><th mat-header-cell *matHeaderCellDef>Jira</th><td mat-cell *matCellDef="let r" class="mono">{{ r.jiraIssueId ?? '' }}</td></ng-container>
            <ng-container matColumnDef="nonSdaProject"><th mat-header-cell *matHeaderCellDef>No SDA</th><td mat-cell *matCellDef="let r">{{ r.nonSdaProject ?? '' }}</td></ng-container>
            <ng-container matColumnDef="timeMinutes"><th mat-header-cell *matHeaderCellDef>Min</th><td mat-cell *matCellDef="let r">{{ r.timeMinutes ?? '' }}</td></ng-container>
            <ng-container matColumnDef="reason"><th mat-header-cell *matHeaderCellDef>Motivo</th><td mat-cell *matCellDef="let r" class="warn-text">{{ r.reason ?? '' }}</td></ng-container>
            <ng-container matColumnDef="matched"><th mat-header-cell *matHeaderCellDef>Casada</th><td mat-cell *matCellDef="let r"><mat-icon [class]="r.matched ? 'ok-text' : 'muted'" [matTooltip]="r.matched ? 'Asociada a un día del panel' : 'No se encontró el día en el panel'">{{ r.matched ? 'link' : 'link_off' }}</mat-icon></td></ng-container>
            <tr mat-header-row *matHeaderRowDef="rowColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: rowColumns"></tr>
            <tr class="mat-row" *matNoDataRow><td class="mat-cell muted" [attr.colspan]="rowColumns.length" style="padding: 16px">Sin filas</td></tr>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    h3 { font-size: 15px; font-weight: 500; margin: 16px 0 8px; }
    tr.clickable { cursor: pointer; }
    tr.clickable:hover td { background: #f0f4f8; }
    tr.selected td { background: #e3f2fd; }
  `],
})
export class FeedbackComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);

  readonly syncColumns = ['submissionDate', 'status', 'rowCount', 'rejectedCount', 'matchedRows', 'unmatchedRows', 'error'];
  readonly fileColumns = ['submissionDate', 'downloadedAt', 'rowCount', 'rejectedCount', 'matchedCount'];
  readonly rowColumns = ['activityDate', 'personId', 'taskTypeCode', 'sdaProjectId', 'jiraIssueId', 'nonSdaProject', 'timeMinutes', 'reason', 'matched'];

  readonly pending = signal<string[]>([]);
  readonly files = signal<FeedbackFile[]>([]);
  readonly syncResults = signal<FeedbackSyncResult[] | null>(null);
  readonly selectedDate = signal<string | null>(null);
  readonly rows = signal<FeedbackRow[]>([]);
  readonly onlyRejected = signal(true);
  readonly busy = signal(false);
  readonly loadingRows = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.feedbackPending().subscribe({ next: (p) => this.pending.set(p), error: (e: unknown) => this.notify.error(e) });
    this.api.feedbackFiles().subscribe({
      next: (list) => this.files.set(list.slice().sort((a, b) => b.submissionDate.localeCompare(a.submissionDate))),
      error: (e: unknown) => this.notify.error(e),
    });
  }

  syncLabel(s: FeedbackSyncStatus): string {
    return SYNC_LABEL[s] ?? s;
  }

  shorten(id: string | null): string {
    if (!id) return '';
    return id.length > 12 ? id.substring(0, 12) + '…' : id;
  }

  sync(): void {
    this.busy.set(true);
    this.api.feedbackSync().subscribe({
      next: (r) => { this.busy.set(false); this.syncResults.set(r); this.load(); if (this.selectedDate()) this.loadRows(); },
      error: (e: unknown) => { this.busy.set(false); this.notify.error(e); },
    });
  }

  select(f: FeedbackFile): void {
    this.selectedDate.set(f.submissionDate);
    this.loadRows();
  }

  toggleOnlyRejected(checked: boolean): void {
    this.onlyRejected.set(checked);
    this.loadRows();
  }

  private loadRows(): void {
    const date = this.selectedDate();
    if (!date) return;
    this.loadingRows.set(true);
    this.api.feedbackRows(date, this.onlyRejected()).subscribe({
      next: (rows) => { this.rows.set(rows); this.loadingRows.set(false); },
      error: (e: unknown) => { this.loadingRows.set(false); this.notify.error(e); },
    });
  }
}
