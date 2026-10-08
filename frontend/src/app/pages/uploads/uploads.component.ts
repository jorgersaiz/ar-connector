import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { UploadPreview, UploadQuota, UploadRecord, UploadRunResult, UploadStatus } from '../../core/models';
import { confirm } from '../../shared/confirm-dialog.component';

const STATUS_LABEL: Record<UploadStatus, string> = {
  SENT: 'Enviado',
  SKIPPED_QUOTA: 'Cuota agotada',
  UNCHANGED: 'Sin cambios desde el último envío',
  NOTHING_TO_SEND: 'Nada que enviar',
  FAILED: 'Error',
  NOT_BUILT: 'No se pudo generar',
  DISABLED: 'Envíos desactivados por configuración',
};

@Component({
  selector: 'app-uploads',
  standalone: true,
  imports: [
    DatePipe, MatCardModule, MatButtonModule, MatIconModule, MatTableModule, MatTooltipModule,
    MatProgressBarModule, MatExpansionModule, MatDialogModule,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Envíos a Activity Report</h1>
        <span class="spacer"></span>
        <button mat-stroked-button (click)="preview()" [disabled]="busy()"><mat-icon>preview</mat-icon> Previsualizar</button>
        <button mat-flat-button color="primary" (click)="run()" [disabled]="busy() || quotaExhausted()"><mat-icon>send</mat-icon> Enviar ahora</button>
      </div>

      @if (busy()) { <mat-progress-bar mode="indeterminate" /> }

      <mat-card class="card">
        <mat-card-header><mat-card-title>Cuota diaria</mat-card-title></mat-card-header>
        <mat-card-content>
          @if (quota(); as q) {
            <div class="stats">
              <div class="stat"><span class="value" [class.warn-text]="quotaExhausted()">{{ q.callsToday }} / {{ q.limit }}</span><span class="label">Llamadas hoy ({{ q.day | date: 'dd/MM/yyyy' }})</span></div>
              <div class="stat"><span class="value">{{ q.lastOk === null ? '—' : (q.lastOk ? 'OK' : 'KO') }}</span><span class="label">Última llamada de hoy</span></div>
              <div class="stat"><span class="value mono" style="font-size: 16px; padding-top: 8px">{{ q.channel }}</span><span class="label">Canal</span></div>
            </div>
          } @else {
            <span class="muted">Cargando cuota…</span>
          }
        </mat-card-content>
      </mat-card>

      @if (result(); as r) {
        <div [class]="r.status === 'SENT' ? 'ok-box' : (r.status === 'FAILED' || r.status === 'NOT_BUILT' ? 'errors-box' : 'info-box')">
          <strong>{{ statusLabel(r.status) }}</strong>
          @if (r.submissionDate) { · fecha de envío {{ r.submissionDate | date: 'dd/MM/yyyy' }} }
          · {{ r.dayCount }} días, {{ r.rowCount }} filas, {{ r.excludedDays }} días excluidos
          @if (r.apiCallId) { · llamada #{{ r.apiCallId }} }
          @if (r.error) { <div style="margin-top: 6px">{{ r.error }}</div> }
        </div>
      }

      @if (previewData(); as p) {
        <mat-card class="card">
          <mat-card-header>
            <mat-card-title>Previsualización · envío del {{ p.submissionDate | date: 'dd/MM/yyyy' }}</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="stats" style="margin-bottom: 12px">
              <div class="stat"><span class="value">{{ p.dayCount }}</span><span class="label">Días</span></div>
              <div class="stat"><span class="value">{{ p.rowCount }}</span><span class="label">Filas CSV</span></div>
              <div class="stat"><span class="value">{{ p.excludedDays }}</span><span class="label">Días excluidos</span></div>
              <div class="stat"><span class="value" [class.warn-text]="p.gaps.length">{{ p.gaps.length }}</span><span class="label">Huecos</span></div>
              <div class="stat"><span class="value" [class.warn-text]="p.problems.length">{{ p.problems.length }}</span><span class="label">Problemas</span></div>
            </div>

            @if (p.gaps.length) {
              <h3>Huecos (laborables sin nada que enviar)</h3>
              <div class="table-wrap">
                <table mat-table [dataSource]="p.gaps">
                  <ng-container matColumnDef="uid"><th mat-header-cell *matHeaderCellDef>UID</th><td mat-cell *matCellDef="let g" class="mono">{{ g.uid }}</td></ng-container>
                  <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Día</th><td mat-cell *matCellDef="let g">{{ g.date | date: 'dd/MM/yyyy' }}</td></ng-container>
                  <ng-container matColumnDef="lastSubmissionDate"><th mat-header-cell *matHeaderCellDef>Límite de envío</th><td mat-cell *matCellDef="let g">{{ g.lastSubmissionDate ? (g.lastSubmissionDate | date: 'dd/MM/yyyy') : '—' }}</td></ng-container>
                  <tr mat-header-row *matHeaderRowDef="gapColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: gapColumns"></tr>
                </table>
              </div>
            }

            @if (p.problems.length) {
              <h3>Problemas</h3>
              <div class="table-wrap">
                <table mat-table [dataSource]="p.problems">
                  <ng-container matColumnDef="uid"><th mat-header-cell *matHeaderCellDef>UID</th><td mat-cell *matCellDef="let x" class="mono">{{ x.uid ?? '' }}</td></ng-container>
                  <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Día</th><td mat-cell *matCellDef="let x">{{ x.date ? (x.date | date: 'dd/MM/yyyy') : '' }}</td></ng-container>
                  <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>Código</th><td mat-cell *matCellDef="let x" class="mono">{{ x.code }}</td></ng-container>
                  <ng-container matColumnDef="message"><th mat-header-cell *matHeaderCellDef>Mensaje</th><td mat-cell *matCellDef="let x">{{ x.message }}</td></ng-container>
                  <tr mat-header-row *matHeaderRowDef="problemColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: problemColumns"></tr>
                </table>
              </div>
            }

            <mat-expansion-panel style="margin-top: 12px">
              <mat-expansion-panel-header><mat-panel-title>CSV generado ({{ p.rowCount }} filas)</mat-panel-title></mat-expansion-panel-header>
              <pre class="csv">{{ p.csv }}</pre>
            </mat-expansion-panel>
          </mat-card-content>
        </mat-card>
      }

      <h3>Histórico de llamadas</h3>
      <div class="table-wrap">
        <table mat-table [dataSource]="history()">
          <ng-container matColumnDef="calledAt"><th mat-header-cell *matHeaderCellDef>Llamada</th><td mat-cell *matCellDef="let u" class="nowrap">{{ u.calledAt | date: 'dd/MM/yyyy HH:mm' }}</td></ng-container>
          <ng-container matColumnDef="submissionDate"><th mat-header-cell *matHeaderCellDef>Fecha envío</th><td mat-cell *matCellDef="let u" class="nowrap">{{ u.submissionDate ? (u.submissionDate | date: 'dd/MM/yyyy') : '' }}</td></ng-container>
          <ng-container matColumnDef="ok"><th mat-header-cell *matHeaderCellDef>OK</th><td mat-cell *matCellDef="let u"><mat-icon [class]="u.ok ? 'ok-text' : 'warn-text'">{{ u.ok ? 'check_circle' : 'error' }}</mat-icon></td></ng-container>
          <ng-container matColumnDef="httpStatus"><th mat-header-cell *matHeaderCellDef>HTTP</th><td mat-cell *matCellDef="let u">{{ u.httpStatus ?? '' }}</td></ng-container>
          <ng-container matColumnDef="dayCount"><th mat-header-cell *matHeaderCellDef>Días</th><td mat-cell *matCellDef="let u">{{ u.dayCount ?? '' }}</td></ng-container>
          <ng-container matColumnDef="rowCount"><th mat-header-cell *matHeaderCellDef>Filas</th><td mat-cell *matCellDef="let u">{{ u.rowCount ?? '' }}</td></ng-container>
          <ng-container matColumnDef="errorMessage"><th mat-header-cell *matHeaderCellDef>Error</th><td mat-cell *matCellDef="let u" class="warn-text small">{{ u.errorMessage ?? '' }}</td></ng-container>
          <ng-container matColumnDef="file">
            <th mat-header-cell *matHeaderCellDef>Fichero</th>
            <td mat-cell *matCellDef="let u">
              <a mat-icon-button [href]="fileUrl(u)" target="_blank" rel="noopener" [matTooltip]="u.fileName ?? 'Abrir CSV'" aria-label="Abrir CSV"><mat-icon>description</mat-icon></a>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="historyColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: historyColumns"></tr>
          <tr class="mat-row" *matNoDataRow><td class="mat-cell muted" [attr.colspan]="historyColumns.length" style="padding: 16px">Sin envíos todavía</td></tr>
        </table>
      </div>
    </div>
  `,
  styles: [`h3 { font-size: 15px; font-weight: 500; margin: 16px 0 8px; }`],
})
export class UploadsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  readonly gapColumns = ['uid', 'date', 'lastSubmissionDate'];
  readonly problemColumns = ['uid', 'date', 'code', 'message'];
  readonly historyColumns = ['calledAt', 'submissionDate', 'ok', 'httpStatus', 'dayCount', 'rowCount', 'errorMessage', 'file'];

  readonly quota = signal<UploadQuota | null>(null);
  readonly previewData = signal<UploadPreview | null>(null);
  readonly result = signal<UploadRunResult | null>(null);
  readonly history = signal<UploadRecord[]>([]);
  readonly busy = signal(false);

  ngOnInit(): void {
    this.loadQuota();
    this.loadHistory();
  }

  quotaExhausted(): boolean {
    const q = this.quota();
    return !!q && q.callsToday >= q.limit;
  }

  statusLabel(s: UploadStatus): string {
    return STATUS_LABEL[s] ?? s;
  }

  fileUrl(u: UploadRecord): string {
    return this.api.uploadFileUrl(u.id);
  }

  loadQuota(): void {
    this.api.uploadQuota().subscribe({ next: (q) => this.quota.set(q), error: (e: unknown) => this.notify.error(e) });
  }

  loadHistory(): void {
    this.api.uploads().subscribe({
      next: (list) => this.history.set(list.slice().sort((a, b) => b.calledAt.localeCompare(a.calledAt))),
      error: (e: unknown) => this.notify.error(e),
    });
  }

  preview(): void {
    this.busy.set(true);
    this.api.uploadPreview().subscribe({
      next: (p) => { this.previewData.set(p); this.busy.set(false); },
      error: (e: unknown) => { this.busy.set(false); this.notify.error(e); },
    });
  }

  run(): void {
    const q = this.quota();
    confirm(this.dialog, {
      title: 'Enviar a Activity Report',
      message: `Se generará el CSV con los días pendientes y se enviará a BBVA.${q ? ` Llamadas hoy: ${q.callsToday} de ${q.limit}.` : ''}\n¿Continuar?`,
      confirmLabel: 'Enviar',
    }).subscribe((ok) => {
      if (!ok) return;
      this.busy.set(true);
      this.result.set(null);
      this.api.uploadRun().subscribe({
        next: (r) => {
          this.busy.set(false);
          this.result.set(r);
          this.previewData.set(null);
          this.loadQuota();
          this.loadHistory();
        },
        error: (e: unknown) => { this.busy.set(false); this.notify.error(e); this.loadQuota(); this.loadHistory(); },
      });
    });
  }
}
