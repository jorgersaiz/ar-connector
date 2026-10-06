import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { fromIso } from '../../core/dates';
import {
  AppConfig, Dashboard, DashboardPerson, Notification, NotificationConfig, NotificationKind, NotificationRunResult,
} from '../../core/models';
import { NotificationDialogComponent, notificationKindLabel } from './notification-dialog.component';

/** Máximo de chips de fechas por celda antes de resumir con "+N". */
const MAX_CHIPS = 6;
/** Umbral (días) a partir del cual una fecha límite se pinta en warn. */
const WARN_DAYS = 2;
const NOTIFICATIONS_LIMIT = 50;

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    DatePipe, RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatTableModule, MatTooltipModule,
    MatProgressBarModule, MatDialogModule,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Situación del periodo</h1>
        @if (appConfig(); as a) {
          <span class="env-chip" [class.env-pro]="a.environment === 'PRO'" [class.env-local]="a.environment === 'LOCAL'"
                [matTooltip]="envTooltip(a)">{{ envLabel(a) }}</span>
        }
        <span class="spacer"></span>
        <button mat-stroked-button (click)="reload()" [disabled]="loading()"><mat-icon>refresh</mat-icon> Actualizar</button>
        <button mat-stroked-button (click)="goToUploads()"><mat-icon>preview</mat-icon> Previsualizar envío</button>
        <button mat-flat-button color="primary" (click)="runNotifications()" [disabled]="sending()"><mat-icon>notifications_active</mat-icon> Enviar avisos ahora</button>
      </div>

      @if (loading() || sending()) { <mat-progress-bar mode="indeterminate" /> }

      @if (config(); as c) {
        @if (c.channel === 'LOG') {
          <div class="info-box hint-box">
            <mat-icon inline>info</mat-icon>
            <span>Sin SMTP configurado: los avisos solo se registran, no se envían por correo.</span>
            <span class="muted">(Se avisa {{ c.daysBeforeCutoff }} {{ c.daysBeforeCutoff === 1 ? 'día' : 'días' }} antes de la fecha límite.)</span>
          </div>
        }
      }

      @if (dashboard(); as d) {
        <!-- Tarjetas de cabecera -->
        <div class="cards">
          @for (p of d.periods; track p.from) {
            <mat-card class="info-card" [class.card-warn]="p.daysLeft <= warnDays && p.daysLeft > 0" [class.card-danger]="p.daysLeft <= 0">
              <mat-card-content>
                <div class="card-label">{{ p.label }}</div>
                <div class="card-value">{{ p.daysLeft }} {{ p.daysLeft === 1 ? 'día' : 'días' }}</div>
                <div class="card-sub">Fecha límite {{ p.lastSubmissionDate | date: 'dd/MM' }} · del {{ p.from | date: 'dd/MM' }} al {{ p.to | date: 'dd/MM' }}</div>
              </mat-card-content>
            </mat-card>
          } @empty {
            <mat-card class="info-card">
              <mat-card-content>
                <div class="card-label">Quincenas abiertas</div>
                <div class="card-value muted">Ninguna</div>
                <div class="card-sub">Hoy {{ d.today | date: 'dd/MM/yyyy' }}</div>
              </mat-card-content>
            </mat-card>
          }

          <mat-card class="info-card" [class.card-danger]="d.quota.callsToday >= d.quota.limit">
            <mat-card-content>
              <div class="card-label">Cuota de envíos hoy</div>
              <div class="card-value">{{ d.quota.callsToday }} / {{ d.quota.limit }}</div>
              <div class="card-sub">Última llamada: {{ d.quota.lastOk === null ? 'ninguna hoy' : (d.quota.lastOk ? 'OK' : 'KO (límite ampliado a ' + d.quota.limit + ')') }}</div>
            </mat-card-content>
          </mat-card>

          <mat-card class="info-card" [class.card-danger]="!!d.lastUpload && !d.lastUpload.ok">
            <mat-card-content>
              <div class="card-label">Último envío</div>
              @if (d.lastUpload; as u) {
                <div class="card-value">
                  <mat-icon [class]="u.ok ? 'ok-text' : 'warn-text'" inline>{{ u.ok ? 'check_circle' : 'error' }}</mat-icon>
                  {{ u.calledAt | date: 'dd/MM HH:mm' }}
                </div>
                <div class="card-sub">
                  {{ u.dayCount ?? 0 }} días · {{ u.rowCount ?? 0 }} filas
                  @if (u.submissionDate) { · fecha de envío {{ u.submissionDate | date: 'dd/MM' }} }
                </div>
                @if (u.errorMessage) { <div class="card-sub warn-text">{{ u.errorMessage }}</div> }
              } @else {
                <div class="card-value muted">Sin envíos</div>
              }
            </mat-card-content>
          </mat-card>

          <mat-card class="info-card" [class.card-warn]="d.pendingFeedbackDates.length > 0">
            <mat-card-content>
              <div class="card-label">Feedback pendiente</div>
              @if (d.pendingFeedbackDates.length) {
                <div class="card-value">{{ d.pendingFeedbackDates.length }}</div>
                <div class="card-sub chips">
                  @for (f of d.pendingFeedbackDates; track f) { <span class="chip-day holiday">{{ f | date: 'dd/MM' }}</span> }
                </div>
              } @else {
                <div class="card-value ok-text">Al día</div>
                <div class="card-sub">No hay feedback por descargar</div>
              }
            </mat-card-content>
          </mat-card>
        </div>

        <!-- Totales -->
        <mat-card class="card">
          <mat-card-content>
            <div class="stats">
              <div class="stat"><span class="value">{{ d.totals.persons }}</span><span class="label">Personas</span></div>
              <div class="stat"><span class="value" [class.warn-text]="d.totals.personsWithGaps > 0">{{ d.totals.personsWithGaps }}</span><span class="label">Con huecos</span></div>
              <div class="stat"><span class="value" [class.warn-text]="d.totals.gaps > 0">{{ d.totals.gaps }}</span><span class="label">Huecos</span></div>
              <div class="stat"><span class="value" [class.warn-text]="d.totals.personsWithRejections > 0">{{ d.totals.personsWithRejections }}</span><span class="label">Con rechazos</span></div>
              <div class="stat"><span class="value" [class.warn-text]="d.totals.rejections > 0">{{ d.totals.rejections }}</span><span class="label">Rechazos</span></div>
              <div class="stat"><span class="value" [class.warn-text]="d.totals.excludedDays > 0">{{ d.totals.excludedDays }}</span><span class="label">Días excluidos</span></div>
              <span class="spacer"></span>
              <div class="stat"><span class="value small-value">{{ d.submissionDate | date: 'dd/MM/yyyy' }}</span><span class="label">Próxima fecha de envío</span></div>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Personas con algo que resolver -->
        <h3>Personas con algo que resolver</h3>
        <div class="table-wrap">
          <table mat-table [dataSource]="d.persons">
            <ng-container matColumnDef="displayName">
              <th mat-header-cell *matHeaderCellDef>Nombre</th>
              <td mat-cell *matCellDef="let p">
                <a [routerLink]="['/personas', p.personId, 'mes']" [matTooltip]="p.email">{{ p.displayName }}</a>
              </td>
            </ng-container>
            <ng-container matColumnDef="uid">
              <th mat-header-cell *matHeaderCellDef>UID</th>
              <td mat-cell *matCellDef="let p" class="mono">{{ p.uid }}</td>
            </ng-container>
            <ng-container matColumnDef="gaps">
              <th mat-header-cell *matHeaderCellDef>Huecos</th>
              <td mat-cell *matCellDef="let p">
                @if (p.gapDates.length) {
                  <span class="count warn-text">{{ p.gapDates.length }}</span>
                  <span class="chips">
                    @for (g of firstDates(p.gapDates); track g) { <span class="chip-day gap">{{ g | date: 'dd/MM' }}</span> }
                    @if (p.gapDates.length > maxChips) { <span class="chip-day" [matTooltip]="restTooltip(p.gapDates)">+{{ p.gapDates.length - maxChips }}</span> }
                  </span>
                } @else { <span class="muted">—</span> }
              </td>
            </ng-container>
            <ng-container matColumnDef="nextGapDeadline">
              <th mat-header-cell *matHeaderCellDef>Fecha límite</th>
              <td mat-cell *matCellDef="let p" class="nowrap" [class.warn-text]="deadlineSoon(p)">
                @if (p.nextGapDeadline) {
                  @if (deadlineSoon(p)) { <mat-icon inline>warning</mat-icon> }
                  {{ p.nextGapDeadline | date: 'dd/MM/yyyy' }}
                  <span class="muted small">({{ daysLeftLabel(p.nextGapDeadline) }})</span>
                } @else { <span class="muted">—</span> }
              </td>
            </ng-container>
            <ng-container matColumnDef="rejections">
              <th mat-header-cell *matHeaderCellDef>Rechazos</th>
              <td mat-cell *matCellDef="let p">
                @if (p.rejectedDates.length) {
                  <span class="count warn-text">{{ p.rejectedDates.length }}</span>
                  <span class="chips">
                    @for (r of firstDates(p.rejectedDates); track r) { <span class="chip-day manual">{{ r | date: 'dd/MM' }}</span> }
                    @if (p.rejectedDates.length > maxChips) { <span class="chip-day" [matTooltip]="restTooltip(p.rejectedDates)">+{{ p.rejectedDates.length - maxChips }}</span> }
                  </span>
                } @else { <span class="muted">—</span> }
              </td>
            </ng-container>
            <ng-container matColumnDef="excluded">
              <th mat-header-cell *matHeaderCellDef>Excluidos</th>
              <td mat-cell *matCellDef="let p">
                @if (p.excludedDates.length) {
                  <span class="count warn-text" [matTooltip]="restTooltip(p.excludedDates, 0)">{{ p.excludedDates.length }}</span>
                  <span class="chips">
                    @for (c of p.problemCodes; track c) { <span class="chip-day nonworking mono">{{ c }}</span> }
                  </span>
                } @else { <span class="muted">—</span> }
              </td>
            </ng-container>
            <ng-container matColumnDef="hasTemplate">
              <th mat-header-cell *matHeaderCellDef>Plantilla</th>
              <td mat-cell *matCellDef="let p">
                <mat-icon [class]="p.hasTemplate ? 'ok-text' : 'warn-text'" [matTooltip]="p.hasTemplate ? 'Tiene plantilla vigente' : 'Sin plantilla vigente'">
                  {{ p.hasTemplate ? 'check_circle' : 'cancel' }}
                </mat-icon>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="personColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: personColumns"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell" [attr.colspan]="personColumns.length" style="padding: 16px">
                <span class="ok-text"><mat-icon inline>task_alt</mat-icon> Todo el mundo está al día</span>
              </td>
            </tr>
          </table>
        </div>
      } @else if (!loading()) {
        <div class="muted" style="padding: 16px 0">No se pudo cargar la situación del periodo.</div>
      }

      <!-- Avisos enviados -->
      <div class="page-header" style="margin-top: 24px">
        <h3 style="margin: 0">Avisos enviados</h3>
        <span class="spacer"></span>
        <span class="muted small">Últimos {{ notificationsLimit }}</span>
      </div>
      @if (loadingNotifications()) { <mat-progress-bar mode="indeterminate" /> }
      <div class="table-wrap">
        <table mat-table [dataSource]="notifications()">
          <ng-container matColumnDef="sentAt"><th mat-header-cell *matHeaderCellDef>Enviado</th><td mat-cell *matCellDef="let n" class="nowrap">{{ n.sentAt | date: 'dd/MM/yyyy HH:mm' }}</td></ng-container>
          <ng-container matColumnDef="kind"><th mat-header-cell *matHeaderCellDef>Tipo</th><td mat-cell *matCellDef="let n" class="nowrap">{{ kindLabel(n.kind) }}</td></ng-container>
          <ng-container matColumnDef="channel"><th mat-header-cell *matHeaderCellDef>Canal</th><td mat-cell *matCellDef="let n"><span class="chip-day" [class.holiday]="n.channel === 'EMAIL'">{{ n.channel }}</span></td></ng-container>
          <ng-container matColumnDef="recipientEmail"><th mat-header-cell *matHeaderCellDef>Destinatario</th><td mat-cell *matCellDef="let n" class="small">{{ n.recipientEmail }}</td></ng-container>
          <ng-container matColumnDef="personDisplayName"><th mat-header-cell *matHeaderCellDef>Persona</th><td mat-cell *matCellDef="let n">{{ n.personDisplayName ?? '' }}</td></ng-container>
          <ng-container matColumnDef="subject"><th mat-header-cell *matHeaderCellDef>Asunto</th><td mat-cell *matCellDef="let n">{{ n.subject }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="notificationColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: notificationColumns" class="clickable" (click)="openNotification(row)" matTooltip="Ver cuerpo del aviso"></tr>
          <tr class="mat-row" *matNoDataRow><td class="mat-cell muted" [attr.colspan]="notificationColumns.length" style="padding: 16px">Sin avisos todavía</td></tr>
        </table>
      </div>
    </div>
  `,
  styles: [`
    h3 { font-size: 15px; font-weight: 500; margin: 16px 0 8px; }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; margin-bottom: 16px; }
    .info-card { border-left: 4px solid transparent; }
    .info-card.card-warn { border-left-color: #f9a825; background: #fffbea; }
    .info-card.card-danger { border-left-color: #b3261e; background: #fdecea; }
    .card-label { font-size: 12px; color: rgba(0,0,0,.6); margin-bottom: 4px; }
    .card-value { font-size: 24px; font-weight: 500; line-height: 1.2; display: flex; align-items: center; gap: 6px; }
    .card-sub { font-size: 12px; color: rgba(0,0,0,.6); margin-top: 4px; }
    .card-danger .card-value { color: #b3261e; }
    .card-warn .card-value { color: #b26a00; }
    .chips { display: inline-flex; flex-wrap: wrap; gap: 4px; vertical-align: middle; }
    .count { font-weight: 500; margin-right: 8px; }
    .small-value { font-size: 18px; padding-top: 6px; }
    .hint-box { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
    .env-chip { margin-left: 12px; padding: 2px 10px; border-radius: 12px; font-size: 12px; font-weight: 500; background: #e3f2fd; color: #0d47a1; cursor: default; }
    .env-chip.env-pro { background: #fdecea; color: #b3261e; }
    .env-chip.env-local { background: #ececec; color: #555; }
    tr.clickable { cursor: pointer; }
    tr.clickable:hover td { background: #f0f4f8; }
    td.mat-mdc-cell { padding-top: 6px; padding-bottom: 6px; }
  `],
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  readonly maxChips = MAX_CHIPS;
  readonly warnDays = WARN_DAYS;
  readonly notificationsLimit = NOTIFICATIONS_LIMIT;
  readonly personColumns = ['displayName', 'uid', 'gaps', 'nextGapDeadline', 'rejections', 'excluded', 'hasTemplate'];
  readonly notificationColumns = ['sentAt', 'kind', 'channel', 'recipientEmail', 'personDisplayName', 'subject'];

  readonly dashboard = signal<Dashboard | null>(null);
  readonly config = signal<NotificationConfig | null>(null);
  readonly appConfig = signal<AppConfig | null>(null);
  readonly notifications = signal<Notification[]>([]);
  readonly loading = signal(false);
  readonly loadingNotifications = signal(false);
  readonly sending = signal(false);

  /** "Hoy" según el backend (evita desfases de zona horaria con el navegador). */
  private readonly today = computed(() => this.dashboard()?.today ?? null);

  ngOnInit(): void {
    this.reload();
    this.loadConfig();
    this.loadNotifications();
  }

  reload(): void {
    this.loading.set(true);
    this.api.dashboard().subscribe({
      next: (d) => { this.dashboard.set(d); this.loading.set(false); },
      error: (e: unknown) => { this.loading.set(false); this.notify.error(e); },
    });
  }

  loadConfig(): void {
    this.api.notificationsConfig().subscribe({ next: (c) => this.config.set(c), error: (e: unknown) => this.notify.error(e) });
    this.api.config().subscribe({ next: (c) => this.appConfig.set(c), error: (e: unknown) => this.notify.error(e) });
  }

  envLabel(a: AppConfig): string {
    switch (a.environment) {
      case 'LOCAL': return 'Sin conexión con BBVA (ficheros de ejemplo)';
      case 'PRO': return 'PRODUCCIÓN';
      case 'OTRO': return 'Entorno: otro';
      default: return 'Entorno de pruebas ' + a.environment;
    }
  }

  envTooltip(a: AppConfig): string {
    if (a.environment === 'LOCAL') return 'Los catálogos y los envíos se leen y escriben en la carpeta de ejemplos; no se habla con Activity Report';
    return a.baseUrl + (a.requestorEmail ? ' · Requestor ' + a.requestorEmail : '')
        + (a.credentialsConfigured ? '' : ' · SIN CREDENCIAL: las llamadas fallarán');
  }

  loadNotifications(): void {
    this.loadingNotifications.set(true);
    this.api.notifications(NOTIFICATIONS_LIMIT).subscribe({
      next: (list) => {
        this.notifications.set(list.slice().sort((a, b) => b.sentAt.localeCompare(a.sentAt)));
        this.loadingNotifications.set(false);
      },
      error: (e: unknown) => { this.loadingNotifications.set(false); this.notify.error(e); },
    });
  }

  goToUploads(): void {
    this.router.navigateByUrl('/envios');
  }

  runNotifications(): void {
    this.sending.set(true);
    this.api.runNotifications().subscribe({
      next: (r: NotificationRunResult) => {
        this.sending.set(false);
        this.notify.ok(`Avisos: ${r.sent} enviados, ${r.skipped} omitidos (canal ${r.channel})`);
        this.loadNotifications();
      },
      error: (e: unknown) => { this.sending.set(false); this.notify.error(e); },
    });
  }

  openNotification(n: Notification): void {
    this.dialog.open<NotificationDialogComponent, Notification>(NotificationDialogComponent, { data: n, width: '640px', maxWidth: '95vw' });
  }

  kindLabel(kind: NotificationKind): string {
    return notificationKindLabel(kind);
  }

  firstDates(dates: string[]): string[] {
    return dates.slice(0, MAX_CHIPS);
  }

  /** Tooltip con las fechas que no caben como chips (o todas, si `from` = 0). */
  restTooltip(dates: string[], from = MAX_CHIPS): string {
    return dates.slice(from).map((d) => this.ddMM(d)).join(', ');
  }

  /** Días naturales desde "hoy" (según el backend) hasta la fecha ISO dada. */
  daysUntil(iso: string): number | null {
    const today = fromIso(this.today());
    const target = fromIso(iso);
    if (!today || !target) return null;
    return Math.round((target.getTime() - today.getTime()) / 86_400_000);
  }

  deadlineSoon(p: DashboardPerson): boolean {
    if (!p.nextGapDeadline) return false;
    const days = this.daysUntil(p.nextGapDeadline);
    return days !== null && days <= WARN_DAYS;
  }

  daysLeftLabel(iso: string): string {
    const days = this.daysUntil(iso);
    if (days === null) return '';
    if (days < 0) return 'vencida';
    if (days === 0) return 'hoy';
    return days === 1 ? '1 día' : `${days} días`;
  }

  private ddMM(iso: string): string {
    const d = fromIso(iso);
    if (!d) return iso;
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
  }
}
