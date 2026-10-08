import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { RegistrationFeedbackResult, RegistrationOverview, RegistrationUploadResult, RegistrationUploadStatus } from '../../core/models';
import { confirm } from '../../shared/confirm-dialog.component';
import { RegistrationChipComponent, rejectReasonText } from '../../shared/registration-chip.component';

const STATUS_LABEL: Record<RegistrationUploadStatus, string> = {
  SENT: 'Enviadas',
  SKIPPED_QUOTA: 'Cuota agotada',
  NOTHING_TO_SEND: 'No hay altas pendientes',
  FAILED: 'Error',
  DISABLED: 'Envíos desactivados por configuración',
};

@Component({
  selector: 'app-registrations',
  standalone: true,
  imports: [
    DatePipe, RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatTableModule, MatTooltipModule,
    MatProgressBarModule, MatDialogModule, RegistrationChipComponent,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Altas en Activity Report</h1>
        <span class="spacer"></span>
        <button mat-stroked-button (click)="sync()" [disabled]="busy()"><mat-icon>sync</mat-icon> Sincronizar feedback</button>
        <button mat-flat-button color="primary" (click)="run()" [disabled]="busy() || quotaExhausted()"><mat-icon>send</mat-icon> Enviar altas pendientes</button>
      </div>

      @if (busy() || loading()) { <mat-progress-bar mode="indeterminate" /> }

      @if (data(); as d) {
        <div class="cards">
          <mat-card class="card">
            <mat-card-header><mat-card-title>Cuota diaria</mat-card-title></mat-card-header>
            <mat-card-content>
              <div class="stats">
                <div class="stat"><span class="value" [class.warn-text]="quotaExhausted()">{{ d.quota.callsToday }} / {{ d.quota.limit }}</span><span class="label">Llamadas hoy</span></div>
                <div class="stat"><span class="value">{{ d.quota.lastOk === null ? '—' : (d.quota.lastOk ? 'OK' : 'KO') }}</span><span class="label">Última llamada de hoy</span></div>
                <div class="stat"><span class="value" [class.warn-text]="pendingCount() > 0">{{ pendingCount() }}</span><span class="label">Pendientes de envío</span></div>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="card">
            <mat-card-header><mat-card-title>Último envío de altas</mat-card-title></mat-card-header>
            <mat-card-content>
              @if (d.lastUpload; as u) {
                <div class="stats">
                  <div class="stat"><span class="value" style="font-size: 18px; padding-top: 6px">{{ u.calledAt | date: 'dd/MM/yyyy HH:mm' }}</span><span class="label">Llamada</span></div>
                  <div class="stat"><span class="value" [class.ok-text]="u.ok" [class.warn-text]="!u.ok">{{ u.ok ? 'OK' : 'KO' }}</span><span class="label">Resultado</span></div>
                  <div class="stat"><span class="value">{{ u.dayCount ?? '—' }}</span><span class="label">Altas enviadas</span></div>
                </div>
                @if (u.errorMessage) { <div class="warn-text small" style="margin-top: 8px">{{ u.errorMessage }}</div> }
              } @else {
                <span class="muted">Sin envíos</span>
              }
            </mat-card-content>
          </mat-card>

          <mat-card class="card">
            <mat-card-header><mat-card-title>Feedback pendiente</mat-card-title></mat-card-header>
            <mat-card-content>
              @if (d.pendingFeedbackDates.length) {
                <ul class="dates">
                  @for (date of d.pendingFeedbackDates; track date) { <li>{{ date | date: 'dd/MM/yyyy' }}</li> }
                </ul>
              } @else {
                <span class="muted">Ningún envío pendiente de feedback</span>
              }
            </mat-card-content>
          </mat-card>
        </div>
      }

      @if (result(); as r) {
        <div [class]="r.status === 'SENT' ? 'ok-box' : (r.status === 'FAILED' ? 'errors-box' : 'info-box')">
          <strong>{{ resultText(r) }}</strong>
          @if (r.submissionDate) { · fecha de envío {{ r.submissionDate | date: 'dd/MM/yyyy' }} }
          @if (r.apiCallId) { · llamada #{{ r.apiCallId }} }
          @if (r.error) { <div style="margin-top: 6px">{{ r.error }}</div> }
        </div>
      }

      @if (syncResults(); as results) {
        @if (results.length) {
          <div class="table-wrap" style="margin-bottom: 16px">
            <table mat-table [dataSource]="results">
              <ng-container matColumnDef="submissionDate"><th mat-header-cell *matHeaderCellDef>Envío</th><td mat-cell *matCellDef="let r" class="nowrap">{{ r.submissionDate | date: 'dd/MM/yyyy' }}</td></ng-container>
              <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Estado</th><td mat-cell *matCellDef="let r" [class]="r.status === 'FAILED' ? 'warn-text' : (r.status === 'APPLIED' ? 'ok-text' : 'muted')">{{ syncLabel(r) }}</td></ng-container>
              <ng-container matColumnDef="registered"><th mat-header-cell *matHeaderCellDef>Registradas</th><td mat-cell *matCellDef="let r">{{ r.registered ?? '' }}</td></ng-container>
              <ng-container matColumnDef="rejected"><th mat-header-cell *matHeaderCellDef>Rechazadas</th><td mat-cell *matCellDef="let r" [class.warn-text]="(r.rejected ?? 0) > 0">{{ r.rejected ?? '' }}</td></ng-container>
              <ng-container matColumnDef="unmatched"><th mat-header-cell *matHeaderCellDef>Sin correspondencia</th><td mat-cell *matCellDef="let r">{{ r.unmatched ?? '' }}</td></ng-container>
              <ng-container matColumnDef="error"><th mat-header-cell *matHeaderCellDef>Error</th><td mat-cell *matCellDef="let r" class="warn-text small">{{ r.error ?? '' }}</td></ng-container>
              <tr mat-header-row *matHeaderRowDef="syncColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: syncColumns"></tr>
            </table>
          </div>
        } @else {
          <div class="info-box">No había feedback de altas que sincronizar.</div>
        }
      }

      <h3>Personas con alta solicitada</h3>
      <div class="table-wrap">
        <table mat-table [dataSource]="data()?.persons ?? []">
          <ng-container matColumnDef="displayName">
            <th mat-header-cell *matHeaderCellDef>Nombre</th>
            <td mat-cell *matCellDef="let p"><a [routerLink]="['/personas', p.id]">{{ p.displayName }}</a></td>
          </ng-container>
          <ng-container matColumnDef="uid"><th mat-header-cell *matHeaderCellDef>UID</th><td mat-cell *matCellDef="let p" class="mono">{{ p.uid }}</td></ng-container>
          <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>Email</th><td mat-cell *matCellDef="let p">{{ p.email }}</td></ng-container>
          <ng-container matColumnDef="startDate"><th mat-header-cell *matHeaderCellDef>Alta</th><td mat-cell *matCellDef="let p" class="nowrap">{{ p.startDate | date: 'dd/MM/yyyy' }}</td></ng-container>
          <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Estado</th><td mat-cell *matCellDef="let p"><app-registration-chip [person]="p" /></td></ng-container>
          <ng-container matColumnDef="sentDate"><th mat-header-cell *matHeaderCellDef>Enviada el</th><td mat-cell *matCellDef="let p" class="nowrap">{{ p.registrationSentDate ? (p.registrationSentDate | date: 'dd/MM/yyyy') : '' }}</td></ng-container>
          <ng-container matColumnDef="reason"><th mat-header-cell *matHeaderCellDef>Motivo</th><td mat-cell *matCellDef="let p" class="small" [class.warn-text]="p.registrationStatus === 'REJECTED'">{{ p.registrationReason ? reasonText(p.registrationReason) : '' }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
          <tr class="mat-row" *matNoDataRow><td class="mat-cell muted" [attr.colspan]="columns.length" style="padding: 16px">Ninguna persona tiene alta solicitada. Se pide desde la lista de personas o al crear/editar una persona.</td></tr>
        </table>
      </div>
      <p class="hint">Las personas registradas tienen como primer día imputable el siguiente al envío; la fecha de alta se ajusta automáticamente.</p>
    </div>
  `,
  styles: [`
    h3 { font-size: 15px; font-weight: 500; margin: 16px 0 8px; }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
    ul.dates { margin: 0; padding-left: 18px; font-size: 13px; }
  `],
})
export class RegistrationsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  readonly columns = ['displayName', 'uid', 'email', 'startDate', 'status', 'sentDate', 'reason'];
  readonly syncColumns = ['submissionDate', 'status', 'registered', 'rejected', 'unmatched', 'error'];

  readonly data = signal<RegistrationOverview | null>(null);
  readonly result = signal<RegistrationUploadResult | null>(null);
  readonly syncResults = signal<RegistrationFeedbackResult[] | null>(null);
  readonly loading = signal(false);
  readonly busy = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.registrations().subscribe({
      next: (d) => {
        this.data.set({ ...d, persons: d.persons.slice().sort((a, b) => a.displayName.localeCompare(b.displayName)) });
        this.loading.set(false);
      },
      error: (e: unknown) => { this.loading.set(false); this.notify.error(e); },
    });
  }

  quotaExhausted(): boolean {
    const q = this.data()?.quota;
    return !!q && q.callsToday >= q.limit;
  }

  pendingCount(): number {
    return (this.data()?.persons ?? []).filter((p) => p.registrationStatus === 'PENDING').length;
  }

  reasonText(code: string): string {
    return rejectReasonText(code);
  }

  resultText(r: RegistrationUploadResult): string {
    return r.status === 'SENT' ? `Enviadas ${r.count} altas` : (STATUS_LABEL[r.status] ?? r.status);
  }

  syncLabel(r: RegistrationFeedbackResult): string {
    switch (r.status) {
      case 'APPLIED': return 'Aplicado';
      case 'NOT_AVAILABLE_YET': return 'Todavía no disponible';
      case 'FAILED': return 'Error';
      default: return r.status;
    }
  }

  run(): void {
    const q = this.data()?.quota;
    confirm(this.dialog, {
      title: 'Enviar altas a Activity Report',
      message: `Se enviarán a BBVA las ${this.pendingCount()} altas pendientes.${q ? ` Llamadas hoy: ${q.callsToday} de ${q.limit}.` : ''}\n¿Continuar?`,
      confirmLabel: 'Enviar',
    }).subscribe((ok) => {
      if (!ok) return;
      this.busy.set(true);
      this.result.set(null);
      this.syncResults.set(null);
      this.api.runRegistrations().subscribe({
        next: (r) => {
          this.busy.set(false);
          this.result.set(r);
          if (r.status === 'FAILED') this.notify.error(r.error ?? 'Error al enviar las altas');
          else this.notify.ok(this.resultText(r));
          this.load();
        },
        error: (e: unknown) => { this.busy.set(false); this.notify.error(e); this.load(); },
      });
    });
  }

  sync(): void {
    this.busy.set(true);
    this.result.set(null);
    this.api.syncRegistrationFeedback().subscribe({
      next: (results) => {
        this.busy.set(false);
        this.syncResults.set(results);
        for (const r of results) {
          const date = r.submissionDate.split('-').reverse().join('/');
          if (r.status === 'APPLIED') this.notify.ok(`Feedback del ${date}: ${r.registered ?? 0} registradas, ${r.rejected ?? 0} rechazadas`);
          else if (r.status === 'FAILED') this.notify.error(`Feedback del ${date}: ${r.error ?? 'error'}`);
          else this.notify.ok(`Feedback del ${date}: todavía no disponible`);
        }
        if (!results.length) this.notify.ok('No había feedback de altas que sincronizar');
        this.load();
      },
      error: (e: unknown) => { this.busy.set(false); this.notify.error(e); },
    });
  }
}
