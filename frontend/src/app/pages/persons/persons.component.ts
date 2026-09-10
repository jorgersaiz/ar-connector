import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { toSignal } from '@angular/core/rxjs-interop';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { Person } from '../../core/models';
import { confirm } from '../../shared/confirm-dialog.component';
import { RegistrationChipComponent } from '../../shared/registration-chip.component';
import { PersonDialogComponent, PersonDialogData } from './person-dialog.component';

@Component({
  selector: 'app-persons',
  standalone: true,
  imports: [
    DatePipe, ReactiveFormsModule, RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatFormFieldModule,
    MatInputModule, MatSlideToggleModule, MatTooltipModule, MatProgressBarModule, MatDialogModule, RegistrationChipComponent,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Personas</h1>
        <span class="spacer"></span>
        <button mat-flat-button color="primary" (click)="openDialog(null)"><mat-icon>person_add</mat-icon> Nueva persona</button>
      </div>

      <div class="toolbar-row">
        <mat-form-field subscriptSizing="dynamic" style="min-width: 280px">
          <mat-label>Filtrar</mat-label>
          <input matInput [formControl]="filter" placeholder="uid, nombre, email, geografía" />
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>
        <mat-slide-toggle [formControl]="onlyActive">Solo activas</mat-slide-toggle>
        <span class="spacer"></span>
        <span class="muted small">{{ filtered().length }} de {{ persons().length }}</span>
      </div>

      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }

      <div class="table-wrap">
        <table mat-table [dataSource]="filtered()">
          <ng-container matColumnDef="uid">
            <th mat-header-cell *matHeaderCellDef>UID</th>
            <td mat-cell *matCellDef="let p" class="mono">{{ p.uid }}</td>
          </ng-container>
          <ng-container matColumnDef="displayName">
            <th mat-header-cell *matHeaderCellDef>Nombre</th>
            <td mat-cell *matCellDef="let p"><a [routerLink]="['/personas', p.id]">{{ p.displayName }}</a></td>
          </ng-container>
          <ng-container matColumnDef="email">
            <th mat-header-cell *matHeaderCellDef>Email</th>
            <td mat-cell *matCellDef="let p">{{ p.email }}</td>
          </ng-container>
          <ng-container matColumnDef="geography">
            <th mat-header-cell *matHeaderCellDef>Geografía</th>
            <td mat-cell *matCellDef="let p">{{ p.geography }}</td>
          </ng-container>
          <ng-container matColumnDef="gpsCode">
            <th mat-header-cell *matHeaderCellDef>GPS</th>
            <td mat-cell *matCellDef="let p" class="mono">{{ p.gpsCode ?? '' }}</td>
          </ng-container>
          <ng-container matColumnDef="startDate">
            <th mat-header-cell *matHeaderCellDef>Alta</th>
            <td mat-cell *matCellDef="let p" class="nowrap">{{ p.startDate | date: 'dd/MM/yyyy' }}</td>
          </ng-container>
          <ng-container matColumnDef="endDate">
            <th mat-header-cell *matHeaderCellDef>Baja</th>
            <td mat-cell *matCellDef="let p" class="nowrap">{{ p.endDate ? (p.endDate | date: 'dd/MM/yyyy') : '' }}</td>
          </ng-container>
          <ng-container matColumnDef="active">
            <th mat-header-cell *matHeaderCellDef>Activa</th>
            <td mat-cell *matCellDef="let p">
              <mat-icon [class]="p.active ? 'ok-text' : 'muted'">{{ p.active ? 'check_circle' : 'cancel' }}</mat-icon>
            </td>
          </ng-container>
          <ng-container matColumnDef="registration">
            <th mat-header-cell *matHeaderCellDef>Alta AR</th>
            <td mat-cell *matCellDef="let p"><app-registration-chip [person]="p" /></td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let p" class="nowrap">
              <a mat-icon-button [routerLink]="['/personas', p.id, 'mes']" matTooltip="Ver mes" aria-label="Ver mes"><mat-icon>calendar_month</mat-icon></a>
              <a mat-icon-button [routerLink]="['/personas', p.id]" matTooltip="Ficha y plantillas" aria-label="Ficha"><mat-icon>badge</mat-icon></a>
              <button mat-icon-button (click)="openDialog(p)" matTooltip="Editar" aria-label="Editar"><mat-icon>edit</mat-icon></button>
              @if (p.registrationStatus === 'NOT_REQUIRED' || p.registrationStatus === 'REJECTED') {
                <button mat-icon-button (click)="requestRegistration(p)" matTooltip="Solicitar alta en AR" aria-label="Solicitar alta en AR"><mat-icon>person_add_alt</mat-icon></button>
              } @else if (p.registrationStatus === 'PENDING') {
                <button mat-icon-button (click)="cancelRegistration(p)" matTooltip="Cancelar alta" aria-label="Cancelar alta"><mat-icon>person_remove</mat-icon></button>
              }
              <button mat-icon-button color="warn" (click)="deactivate(p)" [disabled]="!p.active" matTooltip="Dar de baja" aria-label="Dar de baja"><mat-icon>person_off</mat-icon></button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
          <tr class="mat-row" *matNoDataRow><td class="mat-cell muted" [attr.colspan]="columns.length" style="padding: 16px">Sin personas</td></tr>
        </table>
      </div>
    </div>
  `,
})
export class PersonsComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  readonly columns = ['uid', 'displayName', 'email', 'geography', 'gpsCode', 'startDate', 'endDate', 'active', 'registration', 'actions'];
  readonly persons = signal<Person[]>([]);
  readonly loading = signal(false);
  readonly filter = new FormControl('', { nonNullable: true });
  readonly onlyActive = new FormControl(true, { nonNullable: true });
  private readonly filterValue = toSignal(this.filter.valueChanges, { initialValue: '' });
  private readonly onlyActiveValue = toSignal(this.onlyActive.valueChanges, { initialValue: true });

  readonly filtered = computed(() => {
    const q = this.filterValue().toLowerCase().trim();
    const onlyActive = this.onlyActiveValue();
    return this.persons().filter((p) => {
      if (onlyActive && !p.active) return false;
      if (!q) return true;
      return [p.uid, p.displayName, p.email, p.geography, p.gpsCode ?? ''].some((s) => s.toLowerCase().includes(q));
    });
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.persons().subscribe({
      next: (list) => { this.persons.set(list.slice().sort((a, b) => a.displayName.localeCompare(b.displayName))); this.loading.set(false); },
      error: (err: unknown) => { this.loading.set(false); this.notify.error(err); },
    });
  }

  openDialog(person: Person | null): void {
    this.dialog
      .open<PersonDialogComponent, PersonDialogData, Person | null>(PersonDialogComponent, { data: { person }, width: '640px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) { this.notify.ok(person ? 'Persona actualizada' : 'Persona creada'); this.load(); }
      });
  }

  requestRegistration(p: Person): void {
    const again = p.registrationStatus === 'REJECTED';
    confirm(this.dialog, {
      title: 'Solicitar alta en Activity Report',
      message: `${again ? 'El alta anterior fue rechazada. ' : ''}Se incluirá a ${p.displayName} (${p.uid}) en el próximo envío de altas. Marca solo a personas que nunca han tenido acceso a Activity Report.`,
      confirmLabel: 'Solicitar alta',
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.requestRegistration(p.id).subscribe({
        next: () => { this.notify.ok('Alta pendiente de envío'); this.load(); },
        error: (err: unknown) => this.notify.error(err),
      });
    });
  }

  cancelRegistration(p: Person): void {
    this.api.cancelRegistration(p.id).subscribe({
      next: () => { this.notify.ok('Alta cancelada'); this.load(); },
      error: (err: unknown) => this.notify.error(err),
    });
  }

  deactivate(p: Person): void {
    confirm(this.dialog, {
      title: 'Dar de baja',
      message: `¿Dar de baja a ${p.displayName} (${p.uid})? Dejará de incluirse en los envíos.`,
      confirmLabel: 'Dar de baja',
      warn: true,
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.deactivatePerson(p.id).subscribe({
        next: () => { this.notify.ok('Persona dada de baja'); this.load(); },
        error: (err: unknown) => this.notify.error(err),
      });
    });
  }
}
