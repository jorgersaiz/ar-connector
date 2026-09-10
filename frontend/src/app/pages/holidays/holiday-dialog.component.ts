import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatRadioModule } from '@angular/material/radio';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/api.service';
import { Holiday, HolidayScope } from '../../core/models';
import { errorLines } from '../../core/errors';
import { toIso } from '../../core/dates';

export interface HolidayDialogData {
  geography: string;
  year: number;
}

@Component({
  selector: 'app-holiday-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatRadioModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title>Nuevo festivo</h2>
    @if (saving()) { <mat-progress-bar mode="indeterminate" /> }
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <div class="row">
          <mat-form-field>
            <mat-label>Geografía</mat-label>
            <input matInput formControlName="geography" style="text-transform: uppercase" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Fecha</mat-label>
            <input matInput [matDatepicker]="dp" formControlName="date" />
            <mat-datepicker-toggle matIconSuffix [for]="dp" />
            <mat-datepicker #dp />
          </mat-form-field>
        </div>
        <mat-form-field>
          <mat-label>Descripción</mat-label>
          <input matInput formControlName="description" />
        </mat-form-field>
        <mat-radio-group formControlName="scope" class="scopes">
          <mat-radio-button value="NATIONAL">Nacional</mat-radio-button>
          <mat-radio-button value="LOCAL">Local</mat-radio-button>
        </mat-radio-group>
        <p class="hint">NATIONAL: BBVA ya lo conoce, no se envía. LOCAL: se envía como día no imputable.</p>
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
  styles: [`.scopes { display: flex; gap: 16px; }`],
})
export class HolidayDialogComponent {
  readonly data = inject<HolidayDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<HolidayDialogComponent, Holiday | null>);
  private readonly api = inject(ApiService);

  readonly saving = signal(false);
  readonly errors = signal<string[]>([]);
  readonly form = new FormGroup({
    geography: new FormControl(this.data.geography || 'ES', { nonNullable: true, validators: [Validators.required] }),
    date: new FormControl<Date | null>(new Date(this.data.year, 0, 1), [Validators.required]),
    description: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    scope: new FormControl<HolidayScope>('NATIONAL', { nonNullable: true }),
  });

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errors.set([]);
    this.api.createHoliday({
      geography: v.geography.toUpperCase().trim(),
      date: toIso(v.date)!,
      description: v.description.trim(),
      scope: v.scope,
    }).subscribe({
      next: (h) => { this.saving.set(false); this.ref.close(h); },
      error: (err: unknown) => { this.saving.set(false); this.errors.set(errorLines(err)); },
    });
  }
}
