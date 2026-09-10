import { Component, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/api.service';
import { ImportResult } from '../../core/models';
import { errorLines } from '../../core/errors';

@Component({
  selector: 'app-imports',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, MatProgressBarModule],
  template: `
    <div class="page">
      <div class="page-header"><h1>Importar días desde CSV</h1></div>

      <mat-card class="card">
        <mat-card-header><mat-card-title>Formato esperado</mat-card-title></mat-card-header>
        <mat-card-content>
          <p class="hint">
            Separador <code>;</code>, codificación UTF-8, una fila por línea de imputación. Varias filas con el mismo <code>uid</code> y <code>date</code> forman un mismo día.
            Los días importados sustituyen la excepción existente para ese día (origen "Importado").
            <code>kind</code> es <code>WORKED</code> o <code>NON_WORKING</code>; en días <code>NON_WORKING</code> las columnas de tarea y minutos van vacías.
            Rellena solo el proyecto que exija el tipo de tarea (<code>sda_project_id</code> + <code>jira_issue_id</code>, o <code>non_sda_project</code>).
          </p>
          <pre class="csv">{{ example }}</pre>
        </mat-card-content>
      </mat-card>

      <mat-card class="card">
        <mat-card-content>
          <div class="toolbar-row">
            <input #fileInput type="file" accept=".csv,text/csv" (change)="onFile(fileInput.files)" hidden />
            <button mat-stroked-button (click)="fileInput.click()" [disabled]="busy()"><mat-icon>attach_file</mat-icon> Elegir fichero</button>
            <span class="muted">{{ file()?.name ?? 'Ningún fichero seleccionado' }}</span>
            <span class="spacer"></span>
            <button mat-flat-button color="primary" (click)="upload()" [disabled]="!file() || busy()"><mat-icon>upload</mat-icon> Importar</button>
          </div>
          @if (busy()) { <mat-progress-bar mode="indeterminate" /> }

          @if (result(); as r) {
            <div class="ok-box">
              Importación completada: {{ r.daysCreated }} días creados, {{ r.daysReplaced }} días sustituidos,
              {{ r.lines }} líneas, {{ r.persons }} personas.
            </div>
          }
          @if (errors().length) {
            <div class="errors-box">
              <strong>No se pudo importar</strong>
              <ul>@for (e of errors(); track $index) { <li>{{ e }}</li> }</ul>
            </div>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`code { background: #eee; padding: 0 4px; border-radius: 3px; }`],
})
export class ImportsComponent {
  private readonly api = inject(ApiService);

  readonly example = [
    'uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note',
    'X012345;2026-09-01;WORKED;DEV.CONS;SDA-000123;PROJ-4567;;480;Construcción módulo pagos',
    'X012345;2026-09-02;WORKED;SOP.INC;;;Soporte N2 CRM;240;',
    'X098765;2026-09-02;NON_WORKING;;;;;;Vacaciones',
  ].join('\n');

  readonly file = signal<File | null>(null);
  readonly busy = signal(false);
  readonly result = signal<ImportResult | null>(null);
  readonly errors = signal<string[]>([]);

  onFile(files: FileList | null): void {
    this.file.set(files && files.length ? files[0] : null);
    this.result.set(null);
    this.errors.set([]);
  }

  upload(): void {
    const f = this.file();
    if (!f) return;
    this.busy.set(true);
    this.result.set(null);
    this.errors.set([]);
    this.api.importDays(f).subscribe({
      next: (r) => { this.busy.set(false); this.result.set(r); this.file.set(null); },
      error: (err: unknown) => { this.busy.set(false); this.errors.set(errorLines(err)); },
    });
  }
}
