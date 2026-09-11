import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api.service';
import { CatalogService } from '../../core/catalog.service';
import { NotifyService } from '../../core/notify.service';
import { CatalogStatus, CatalogSyncResult } from '../../core/models';

@Component({
  selector: 'app-catalogs',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatTableModule, MatProgressBarModule, MatCardModule, MatTooltipModule],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Catálogos de Activity Report</h1>
        <span class="spacer"></span>
        <button mat-flat-button color="primary" (click)="sync()" [disabled]="busy()"><mat-icon>sync</mat-icon> Sincronizar ahora</button>
      </div>
      <p class="hint">
        BBVA publica cada madrugada estas listas y el conector las descarga a las 4:00. Alimentan los selectores al editar un día
        y las validaciones que evitan rechazos. Si un proyecto nuevo no aparece en el selector, comprueba aquí que su catálogo se
        ha sincronizado hoy; "Sincronizar ahora" no espera a la madrugada.
      </p>
      @if (busy()) { <mat-progress-bar mode="indeterminate" /> }

      @if (results(); as rs) {
        <mat-card class="card">
          <mat-card-header><mat-card-title>Resultado de la sincronización</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="table-wrap">
              <table mat-table [dataSource]="rs">
                <ng-container matColumnDef="catalog"><th mat-header-cell *matHeaderCellDef>Catálogo</th><td mat-cell *matCellDef="let r" class="mono">{{ r.catalog }}</td></ng-container>
                <ng-container matColumnDef="success"><th mat-header-cell *matHeaderCellDef>OK</th><td mat-cell *matCellDef="let r"><mat-icon [class]="r.success ? 'ok-text' : 'warn-text'">{{ r.success ? 'check_circle' : 'error' }}</mat-icon></td></ng-container>
                <ng-container matColumnDef="rowCount"><th mat-header-cell *matHeaderCellDef>Filas</th><td mat-cell *matCellDef="let r">{{ r.rowCount ?? '' }}</td></ng-container>
                <ng-container matColumnDef="millis"><th mat-header-cell *matHeaderCellDef>Duración</th><td mat-cell *matCellDef="let r">{{ r.millis }} ms</td></ng-container>
                <ng-container matColumnDef="error"><th mat-header-cell *matHeaderCellDef>Error</th><td mat-cell *matCellDef="let r" class="warn-text small">{{ r.error ?? '' }}</td></ng-container>
                <tr mat-header-row *matHeaderRowDef="resultColumns"></tr>
                <tr mat-row *matRowDef="let row; columns: resultColumns"></tr>
              </table>
            </div>
          </mat-card-content>
        </mat-card>
      }

      <div class="table-wrap">
        <table mat-table [dataSource]="status()">
          <ng-container matColumnDef="catalog"><th mat-header-cell *matHeaderCellDef>Catálogo</th><td mat-cell *matCellDef="let s" class="mono">{{ s.catalog }}</td></ng-container>
          <ng-container matColumnDef="what">
            <th mat-header-cell *matHeaderCellDef>Qué es</th>
            <td mat-cell *matCellDef="let s">
              {{ help[s.catalog]?.what ?? '' }}
              @if (help[s.catalog]?.detail; as d) { <mat-icon class="help-icon" [matTooltip]="d" matTooltipPosition="right">help_outline</mat-icon> }
            </td>
          </ng-container>
          <ng-container matColumnDef="lastSuccessAt"><th mat-header-cell *matHeaderCellDef>Última sincronización OK</th><td mat-cell *matCellDef="let s">{{ s.lastSuccessAt ? (s.lastSuccessAt | date: 'dd/MM/yyyy HH:mm') : 'Nunca' }}</td></ng-container>
          <ng-container matColumnDef="lastAttemptAt"><th mat-header-cell *matHeaderCellDef>Último intento</th><td mat-cell *matCellDef="let s">{{ s.lastAttemptAt ? (s.lastAttemptAt | date: 'dd/MM/yyyy HH:mm') : '' }}</td></ng-container>
          <ng-container matColumnDef="rowCount"><th mat-header-cell *matHeaderCellDef>Filas</th><td mat-cell *matCellDef="let s">{{ s.rowCount ?? '' }}</td></ng-container>
          <ng-container matColumnDef="lastError"><th mat-header-cell *matHeaderCellDef>Último error</th><td mat-cell *matCellDef="let s" class="warn-text small">{{ s.lastError ?? '' }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
          <tr class="mat-row" *matNoDataRow><td class="mat-cell muted" [attr.colspan]="columns.length" style="padding: 16px">Sin información de catálogos</td></tr>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .help-icon { font-size: 18px; width: 18px; height: 18px; vertical-align: middle; margin-left: 4px; color: #777; cursor: help; }
  `],
})
export class CatalogsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly catalogs = inject(CatalogService);
  private readonly notify = inject(NotifyService);

  /** Qué es cada catálogo, en lenguaje de negocio, y para qué lo usa el conector. */
  readonly help: Record<string, { what: string; detail: string }> = {
    task_types: {
      what: 'Árbol de actividades',
      detail: 'Las actividades a las que se puede imputar (desarrollo, soporte, formación...). Es el selector "Tipo de tarea" al editar un día. Solo las actividades finales son imputables, y cada una dice si exige proyecto SDA, issue de Jira o proyecto no SDA.',
    },
    sda_projects: {
      what: 'Proyectos SDA',
      detail: 'Los proyectos SDA con su estado (Flow, On hold, Ended...) y país. Un proyecto cerrado o en pausa solo admite horas durante el margen de trimestres que fija su país.',
    },
    jira_issues: {
      what: 'Issues de Jira',
      detail: 'Features, tasks e issues de conceptualización de Jira, cada una con su proyecto SDA y su estado. Una issue descartada no admite horas; una desplegada solo durante 30 días.',
    },
    jira_issues_historical: {
      what: 'Cambios de estado de issues',
      detail: 'Cuándo cambió de estado cada issue en los últimos 30 días. Sirve para saber hasta qué día se puede imputar a una issue desplegada o descartada.',
    },
    jira_issue_sda_projects_historical: {
      what: 'Cambios de SDA de las features',
      detail: 'Qué proyecto SDA tenía cada feature en cada momento de la quincena. Permite aceptar una feature que cambió de SDA a mitad de periodo.',
    },
    non_sda_projects: {
      what: 'Proyectos no SDA',
      detail: 'Los proyectos que no son SDA, identificados por su nombre. Es el selector "Proyecto no SDA" en las actividades que lo exigen.',
    },
    countries: {
      what: 'Márgenes por país',
      detail: 'Para cada país, cuántos trimestres se puede seguir imputando a un proyecto SDA cerrado (Ended) o en pausa (On hold).',
    },
    hidden_task_types: {
      what: 'Actividades vetadas por persona',
      detail: 'Actividades a las que una persona concreta no puede imputar un día concreto, según BBVA. El conector no envía esos días y los muestra como excluidos.',
    },
  };

  readonly columns = ['catalog', 'what', 'lastSuccessAt', 'lastAttemptAt', 'rowCount', 'lastError'];
  readonly resultColumns = ['catalog', 'success', 'rowCount', 'millis', 'error'];
  readonly status = signal<CatalogStatus[]>([]);
  readonly results = signal<CatalogSyncResult[] | null>(null);
  readonly busy = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.catalogStatus().subscribe({ next: (s) => this.status.set(s), error: (e: unknown) => this.notify.error(e) });
  }

  sync(): void {
    this.busy.set(true);
    this.api.syncCatalogs().subscribe({
      next: (r) => {
        this.busy.set(false);
        this.results.set(r);
        this.catalogs.invalidate();
        this.load();
        this.notify.ok(r.every((x) => x.success) ? 'Catálogos sincronizados' : 'Sincronización con errores');
      },
      error: (e: unknown) => { this.busy.set(false); this.notify.error(e); },
    });
  }
}
