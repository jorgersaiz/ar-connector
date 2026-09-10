import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCardModule } from '@angular/material/card';
import { ApiService } from '../../core/api.service';
import { CatalogService } from '../../core/catalog.service';
import { NotifyService } from '../../core/notify.service';
import { CatalogStatus, CatalogSyncResult } from '../../core/models';

@Component({
  selector: 'app-catalogs',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatTableModule, MatProgressBarModule, MatCardModule],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Catálogos de Activity Report</h1>
        <span class="spacer"></span>
        <button mat-flat-button color="primary" (click)="sync()" [disabled]="busy()"><mat-icon>sync</mat-icon> Sincronizar ahora</button>
      </div>
      <p class="hint">Tipos de tarea, proyectos SDA, issues Jira y proyectos no SDA se descargan de BBVA. La sincronización también se ejecuta de forma programada.</p>
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
})
export class CatalogsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly catalogs = inject(CatalogService);
  private readonly notify = inject(NotifyService);

  readonly columns = ['catalog', 'lastSuccessAt', 'lastAttemptAt', 'rowCount', 'lastError'];
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
