import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/api.service';
import { Person, PersonInput } from '../../core/models';
import { errorLines } from '../../core/errors';
import { fromIso, toIso } from '../../core/dates';

export interface PersonDialogData {
  person: Person | null;
}

@Component({
  selector: 'app-person-dialog',
  standalone: true,
  imports: [
    DatePipe, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatDatepickerModule, MatSlideToggleModule, MatCheckboxModule, MatProgressBarModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.person ? 'Editar persona' : 'Nueva persona' }}</h2>
    @if (saving()) { <mat-progress-bar mode="indeterminate" /> }
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <div class="row">
          <mat-form-field>
            <mat-label>UID (BBVA)</mat-label>
            <input matInput formControlName="uid" />
            <mat-hint>Identificador de usuario en Activity Report</mat-hint>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Código GPS</mat-label>
            <input matInput formControlName="gpsCode" />
          </mat-form-field>
        </div>
        <mat-form-field>
          <mat-label>Nombre</mat-label>
          <input matInput formControlName="displayName" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" />
        </mat-form-field>
        <div class="row">
          <mat-form-field>
            <mat-label>Geografía</mat-label>
            <input matInput formControlName="geography" (blur)="upper()" style="text-transform: uppercase" />
            <mat-hint>Ej. ES, MX, PE</mat-hint>
          </mat-form-field>
        </div>
        <div class="row">
          <mat-form-field>
            <mat-label>Alta</mat-label>
            <input matInput [matDatepicker]="dpFrom" formControlName="startDate" />
            <mat-datepicker-toggle matIconSuffix [for]="dpFrom" />
            <mat-datepicker #dpFrom />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Baja</mat-label>
            <input matInput [matDatepicker]="dpTo" formControlName="endDate" />
            <mat-datepicker-toggle matIconSuffix [for]="dpTo" />
            <mat-datepicker #dpTo />
          </mat-form-field>
        </div>
        <mat-slide-toggle formControlName="active">Activa</mat-slide-toggle>

        @if (canEditRegistration) {
          <mat-checkbox formControlName="needsRegistration" style="margin-top: 8px">Necesita alta en Activity Report (nunca ha tenido acceso)</mat-checkbox>
        } @else if (data.person?.registrationStatus === 'SENT') {
          <div class="hint">Alta en AR: enviada el {{ data.person?.registrationSentDate | date: 'dd/MM/yyyy' }}</div>
        } @else if (data.person?.registrationStatus === 'REGISTERED') {
          <div class="hint">Alta en AR: registrada</div>
        }

        @if (errors().length) {
          <div class="errors-box"><ul>@for (e of errors(); track $index) { <li>{{ e }}</li> }</ul></div>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(null)" [disabled]="saving()">Cancelar</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="form.invalid || saving()">Guardar</button>
    </mat-dialog-actions>
  `,
})
export class PersonDialogComponent {
  readonly data = inject<PersonDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<PersonDialogComponent, Person | null>);
  private readonly api = inject(ApiService);

  readonly saving = signal(false);
  readonly errors = signal<string[]>([]);

  /** El alta solo se pide/desiste al crear o mientras el conector no la haya enviado (NOT_REQUIRED, PENDING, REJECTED). */
  readonly canEditRegistration = !this.data.person || ['NOT_REQUIRED', 'PENDING', 'REJECTED'].includes(this.data.person.registrationStatus);

  readonly form = new FormGroup({
    uid: new FormControl(this.data.person?.uid ?? '', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl(this.data.person?.email ?? '', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    displayName: new FormControl(this.data.person?.displayName ?? '', { nonNullable: true, validators: [Validators.required] }),
    gpsCode: new FormControl<string>(this.data.person?.gpsCode ?? '', { nonNullable: true, validators: [Validators.required] }),
    geography: new FormControl(this.data.person?.geography ?? 'ES', { nonNullable: true, validators: [Validators.required] }),
    startDate: new FormControl<Date | null>(fromIso(this.data.person?.startDate) ?? new Date(), [Validators.required]),
    endDate: new FormControl<Date | null>(fromIso(this.data.person?.endDate)),
    active: new FormControl<boolean>(this.data.person?.active ?? true, { nonNullable: true }),
    needsRegistration: new FormControl<boolean>(this.data.person?.registrationStatus === 'PENDING', { nonNullable: true }),
  });

  upper(): void {
    const c = this.form.controls.geography;
    c.setValue(c.value.toUpperCase().trim());
  }

  save(): void {
    if (this.form.invalid) return;
    this.upper();
    const v = this.form.getRawValue();
    const body: PersonInput = {
      uid: v.uid.trim(),
      email: v.email.trim(),
      displayName: v.displayName.trim(),
      gpsCode: v.gpsCode.trim(),
      geography: v.geography,
      startDate: toIso(v.startDate)!,
      endDate: toIso(v.endDate),
      active: v.active,
    };
    if (this.canEditRegistration) body.registrationStatus = v.needsRegistration ? 'PENDING' : 'NOT_REQUIRED';
    this.saving.set(true);
    this.errors.set([]);
    const req = this.data.person ? this.api.updatePerson(this.data.person.id, body) : this.api.createPerson(body);
    req.subscribe({
      next: (p) => { this.saving.set(false); this.ref.close(p); },
      error: (err: unknown) => { this.saving.set(false); this.errors.set(errorLines(err)); },
    });
  }
}
