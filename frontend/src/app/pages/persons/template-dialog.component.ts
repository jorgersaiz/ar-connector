import { Component, ViewChild, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/api.service';
import { Line, Template } from '../../core/models';
import { errorLines } from '../../core/errors';
import { toIso } from '../../core/dates';
import { LinesEditorComponent } from '../../shared/lines-editor.component';

export interface TemplateDialogData {
  personId: number;
  /** Líneas de partida (p. ej. para duplicar una plantilla existente). */
  initialLines?: Line[];
}

@Component({
  selector: 'app-template-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatDatepickerModule, MatProgressBarModule, LinesEditorComponent,
  ],
  template: `
    <h2 mat-dialog-title>Nueva plantilla</h2>
    @if (saving()) { <mat-progress-bar mode="indeterminate" /> }
    <mat-dialog-content>
      <div class="dialog-form">
        <p class="hint">Se aplica a todos los días laborables del periodo que no tengan una excepción. Si ya hay una plantilla sin fin que empieza antes, se cerrará el día anterior a esta fecha de inicio.</p>
        <form [formGroup]="form" class="row">
          <mat-form-field>
            <mat-label>Válida desde</mat-label>
            <input matInput [matDatepicker]="dpFrom" formControlName="validFrom" />
            <mat-datepicker-toggle matIconSuffix [for]="dpFrom" />
            <mat-datepicker #dpFrom />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Válida hasta</mat-label>
            <input matInput [matDatepicker]="dpTo" formControlName="validTo" />
            <mat-datepicker-toggle matIconSuffix [for]="dpTo" />
            <mat-datepicker #dpTo />
            <mat-hint>Vacío = sin fin</mat-hint>
          </mat-form-field>
        </form>
        <app-lines-editor [initial]="data.initialLines ?? []" />
        @if (errors().length) {
          <div class="errors-box"><ul>@for (e of errors(); track $index) { <li>{{ e }}</li> }</ul></div>
        }
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(null)" [disabled]="saving()">Cancelar</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="form.invalid || saving()">Crear plantilla</button>
    </mat-dialog-actions>
  `,
})
export class TemplateDialogComponent {
  readonly data = inject<TemplateDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<TemplateDialogComponent, Template | null>);
  private readonly api = inject(ApiService);

  @ViewChild(LinesEditorComponent) editor?: LinesEditorComponent;

  readonly saving = signal(false);
  readonly errors = signal<string[]>([]);
  readonly form = new FormGroup({
    validFrom: new FormControl<Date | null>(new Date(), [Validators.required]),
    validTo: new FormControl<Date | null>(null),
  });

  save(): void {
    const editor = this.editor;
    if (!editor || this.form.invalid) return;
    editor.touch();
    this.errors.set([]);
    if (!editor.valid) {
      this.errors.set(['Revisa las líneas: todas necesitan tipo de tarea, proyecto (si aplica) y minutos entre 1 y 1440.']);
      return;
    }
    const lines = editor.value();
    if (lines.length === 0) {
      this.errors.set(['La plantilla necesita al menos una línea.']);
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.api.createTemplate(this.data.personId, { validFrom: toIso(v.validFrom)!, validTo: toIso(v.validTo), lines }).subscribe({
      next: (t) => { this.saving.set(false); this.ref.close(t); },
      error: (err: unknown) => { this.saving.set(false); this.errors.set(errorLines(err)); },
    });
  }
}
