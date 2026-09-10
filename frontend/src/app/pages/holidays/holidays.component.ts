import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { Holiday } from '../../core/models';
import { confirm } from '../../shared/confirm-dialog.component';
import { HolidayDialogComponent, HolidayDialogData } from './holiday-dialog.component';

@Component({
  selector: 'app-holidays',
  standalone: true,
  imports: [
    DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatTableModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatTooltipModule, MatProgressBarModule, MatDialogModule,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Festivos</h1>
        <span class="spacer"></span>
        <button mat-flat-button color="primary" (click)="create()"><mat-icon>add</mat-icon> Nuevo festivo</button>
      </div>
      <p class="hint">Los festivos nacionales los conoce BBVA y no se envían; los locales se envían como día no imputable para las personas de esa geografía.</p>

      <div class="toolbar-row">
        <mat-form-field subscriptSizing="dynamic" style="width: 140px">
          <mat-label>Año</mat-label>
          <mat-select [formControl]="year" (selectionChange)="load()">
            @for (y of years; track y) { <mat-option [value]="y">{{ y }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field subscriptSizing="dynamic" style="width: 180px">
          <mat-label>Geografía</mat-label>
          <input matInput [formControl]="geography" (change)="load()" placeholder="Todas" style="text-transform: uppercase" />
        </mat-form-field>
        <button mat-icon-button (click)="load()" matTooltip="Recargar" aria-label="Recargar"><mat-icon>refresh</mat-icon></button>
      </div>
      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }

      <div class="table-wrap">
        <table mat-table [dataSource]="holidays()">
          <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Fecha</th><td mat-cell *matCellDef="let h" class="nowrap">{{ h.date | date: 'EEE dd/MM/yyyy' }}</td></ng-container>
          <ng-container matColumnDef="geography"><th mat-header-cell *matHeaderCellDef>Geografía</th><td mat-cell *matCellDef="let h">{{ h.geography }}</td></ng-container>
          <ng-container matColumnDef="description"><th mat-header-cell *matHeaderCellDef>Descripción</th><td mat-cell *matCellDef="let h">{{ h.description }}</td></ng-container>
          <ng-container matColumnDef="scope">
            <th mat-header-cell *matHeaderCellDef>Ámbito</th>
            <td mat-cell *matCellDef="let h"><span class="chip-day" [class.holiday]="h.scope === 'NATIONAL'" [class.nonworking]="h.scope === 'LOCAL'">{{ h.scope === 'NATIONAL' ? 'Nacional' : 'Local' }}</span></td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let h"><button mat-icon-button color="warn" (click)="remove(h)" matTooltip="Eliminar" aria-label="Eliminar"><mat-icon>delete</mat-icon></button></td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
          <tr class="mat-row" *matNoDataRow><td class="mat-cell muted" [attr.colspan]="columns.length" style="padding: 16px">Sin festivos para el filtro</td></tr>
        </table>
      </div>
    </div>
  `,
})
export class HolidaysComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  readonly columns = ['date', 'geography', 'description', 'scope', 'actions'];
  readonly years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - 2 + i);
  readonly year = new FormControl(new Date().getFullYear(), { nonNullable: true });
  readonly geography = new FormControl('', { nonNullable: true });
  readonly holidays = signal<Holiday[]>([]);
  readonly loading = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const geo = this.geography.value.toUpperCase().trim();
    this.api.holidays(geo || null, this.year.value).subscribe({
      next: (list) => { this.holidays.set(list.slice().sort((a, b) => a.date.localeCompare(b.date) || a.geography.localeCompare(b.geography))); this.loading.set(false); },
      error: (e: unknown) => { this.loading.set(false); this.notify.error(e); },
    });
  }

  create(): void {
    this.dialog
      .open<HolidayDialogComponent, HolidayDialogData, Holiday | null>(HolidayDialogComponent, {
        data: { geography: this.geography.value.toUpperCase().trim(), year: this.year.value },
        width: '520px',
      })
      .afterClosed()
      .subscribe((h) => { if (h) { this.notify.ok('Festivo creado'); this.load(); } });
  }

  remove(h: Holiday): void {
    confirm(this.dialog, {
      title: 'Eliminar festivo',
      message: `¿Eliminar "${h.description}" (${h.geography}, ${h.date})?`,
      confirmLabel: 'Eliminar',
      warn: true,
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.deleteHoliday(h.id).subscribe({
        next: () => { this.notify.ok('Festivo eliminado'); this.load(); },
        error: (e: unknown) => this.notify.error(e),
      });
    });
  }
}
