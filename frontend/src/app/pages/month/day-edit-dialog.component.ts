import { Component, ViewChild, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/api.service';
import { DayKind, DayView } from '../../core/models';
import { errorLines } from '../../core/errors';
import { LinesEditorComponent } from '../../shared/lines-editor.component';
import { HoursPipe } from '../../shared/line-label.pipe';

export interface DayEditData {
  personId: number;
  day: DayView;
}

@Component({
  selector: 'app-day-edit-dialog',
  standalone: true,
  imports: [
    DatePipe, FormsModule, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatButtonToggleModule,
    MatFormFieldModule, MatInputModule, MatProgressBarModule, LinesEditorComponent, HoursPipe,
  ],
  template: `
    <h2 mat-dialog-title>Editar {{ data.day.date | date: 'EEEE d MMMM yyyy' }}</h2>
    @if (saving()) { <mat-progress-bar mode="indeterminate" /> }
    <mat-dialog-content>
      <div class="dialog-form">
        <div class="toolbar-row">
          <mat-button-toggle-group [(ngModel)]="kind" aria-label="Tipo de día">
            <mat-button-toggle value="WORKED">Trabajado</mat-button-toggle>
            <mat-button-toggle value="NON_WORKING">No imputable</mat-button-toggle>
          </mat-button-toggle-group>
          @if (data.day.lastSubmissionDate) {
            <span class="muted small">Fecha límite de envío: {{ data.day.lastSubmissionDate | date: 'dd/MM/yyyy' }}</span>
          }
        </div>

        <mat-form-field>
          <mat-label>Nota</mat-label>
          <input matInput [formControl]="note" maxlength="500" />
        </mat-form-field>

        @if (kind === 'WORKED') {
          <app-lines-editor [initial]="data.day.lines" />
        } @else {
          <p class="hint">El día se enviará como no imputable (vacaciones, baja, permiso…). Las líneas actuales se descartarán.</p>
        }

        @if (errors().length) {
          <div class="errors-box">
            <ul>
              @for (e of errors(); track $index) { <li>{{ e }}</li> }
            </ul>
          </div>
        }
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (kind === 'WORKED' && editor) {
        <span class="muted small" style="margin-right:auto">Total del día: {{ editor.total() | hours }}</span>
      }
      <button mat-button (click)="ref.close(false)" [disabled]="saving()">Cancelar</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">Guardar</button>
    </mat-dialog-actions>
  `,
})
export class DayEditDialogComponent {
  readonly data = inject<DayEditData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<DayEditDialogComponent, boolean>);
  private readonly api = inject(ApiService);

  @ViewChild(LinesEditorComponent) editor?: LinesEditorComponent;

  kind: DayKind = this.data.day.kind ?? 'WORKED';
  readonly note = new FormControl<string>(this.data.day.note ?? '', { nonNullable: true });
  readonly saving = signal(false);
  readonly errors = signal<string[]>([]);

  save(): void {
    this.errors.set([]);
    let lines = undefined;
    if (this.kind === 'WORKED') {
      const editor = this.editor;
      if (!editor) return;
      editor.touch();
      if (!editor.valid) {
        this.errors.set(['Revisa las líneas: todas necesitan tipo de tarea, proyecto (si aplica) y minutos entre 1 y 1440.']);
        return;
      }
      lines = editor.value();
      if (lines.length === 0) {
        this.errors.set(['Un día trabajado necesita al menos una línea.']);
        return;
      }
    }
    this.saving.set(true);
    const note = this.note.value.trim();
    this.api.upsertDay(this.data.personId, this.data.day.date, { kind: this.kind, note: note || null, lines: lines ?? [] }).subscribe({
      next: () => {
        this.saving.set(false);
        this.ref.close(true);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.errors.set(errorLines(err));
      },
    });
  }
}
