import { Component, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api.service';
import { ImportResult } from '../../core/models';
import { errorLines } from '../../core/errors';

interface ColumnHelp {
  name: string;
  required: 'Sí' | 'No' | 'Según la actividad';
  what: string;
  example: string;
}

@Component({
  selector: 'app-imports',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatTableModule, MatTooltipModule],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Importar días desde CSV</h1>
        <span class="spacer"></span>
        <button mat-stroked-button (click)="downloadTemplate()"><mat-icon>download</mat-icon> Descargar plantilla</button>
      </div>

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
              <strong>No se pudo importar. No se ha guardado nada; corrige estas filas y vuelve a subir el fichero.</strong>
              <ul>@for (e of errors(); track $index) { <li>{{ e }}</li> }</ul>
            </div>
          }
        </mat-card-content>
      </mat-card>

      <mat-card class="card">
        <mat-card-header>
          <mat-card-title><mat-icon class="title-icon">help_outline</mat-icon> Cómo preparar el fichero</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <p class="hint">
            Sirve para cargar de golpe las <b>excepciones</b> de una o varias personas: vacaciones, días repartidos entre proyectos,
            semanas en otro proyecto. Lo habitual de cada persona no hace falta importarlo: va en su plantilla por defecto.
            Se puede preparar en Excel y guardar como "CSV (delimitado por punto y coma)".
          </p>

          <h3>Las columnas, en este orden</h3>
          <div class="tablewrap">
            <table mat-table [dataSource]="columns" class="cols">
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Columna</th>
                <td mat-cell *matCellDef="let c"><code>{{ c.name }}</code></td>
              </ng-container>
              <ng-container matColumnDef="required">
                <th mat-header-cell *matHeaderCellDef>Obligatoria</th>
                <td mat-cell *matCellDef="let c">{{ c.required }}</td>
              </ng-container>
              <ng-container matColumnDef="what">
                <th mat-header-cell *matHeaderCellDef>Qué es</th>
                <td mat-cell *matCellDef="let c">{{ c.what }}</td>
              </ng-container>
              <ng-container matColumnDef="example">
                <th mat-header-cell *matHeaderCellDef>Ejemplo</th>
                <td mat-cell *matCellDef="let c"><code>{{ c.example }}</code></td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['name', 'required', 'what', 'example']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['name', 'required', 'what', 'example']"></tr>
            </table>
          </div>

          <h3>Un ejemplo con los tres casos habituales</h3>
          <pre class="csv">{{ header }}</pre>
          <div class="cases">
            <div class="case">
              <pre class="csv">{{ caseSplit }}</pre>
              <p><b>Un día repartido.</b> Dos filas con el mismo uid y fecha son dos líneas del mismo día: 7 horas de construcción en el SDA y 1 hora de formación. La suma no puede pasar de 1440 minutos.</p>
            </div>
            <div class="case">
              <pre class="csv">{{ caseFeature }}</pre>
              <p><b>Una feature de Jira.</b> La actividad exige la issue y su proyecto SDA; los dos van informados. El proyecto no SDA queda vacío.</p>
            </div>
            <div class="case">
              <pre class="csv">{{ caseOff }}</pre>
              <p><b>Un día no imputable.</b> Vacaciones, baja, asuntos propios. Se pone <code>NON_WORKING</code> y todo lo demás vacío salvo la nota, que es opcional.</p>
            </div>
          </div>

          <h3>Reglas que conviene saber</h3>
          <ul class="rules">
            <li><b>Cada día del fichero sustituye por completo</b> al que hubiera para esa persona. Si un día tenía dos líneas y solo importas una, se queda con una.</li>
            <li><b>Es todo o nada.</b> Si una fila falla, no se guarda ninguna y se indica el número de línea y el motivo.</li>
            <li>Se aplican <b>las mismas validaciones que en pantalla</b>: la actividad tiene que existir y ser imputable, el proyecto o la issue tienen que ser los que exige la actividad, y se comprueban las reglas de BBVA (proyectos cerrados, issues desplegadas, nodos vetados).</li>
            <li>Los <b>códigos de actividad, SDA e issue</b> son los del catálogo de BBVA. Los ves en los selectores al editar un día en el mes de cualquier persona.</li>
            <li>Los <b>fines de semana y festivos nacionales</b> no se envían a BBVA aunque vengan en el fichero.</li>
            <li>Un día cuyo <b>periodo ya cerró</b> en BBVA (después del 19 para la 1ª quincena, del 4 del mes siguiente para la 2ª) no se puede importar.</li>
          </ul>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    code { background: #eee; padding: 0 4px; border-radius: 3px; white-space: nowrap; }
    .title-icon { vertical-align: middle; margin-right: 6px; }
    h3 { margin: 20px 0 8px; font-size: 15px; font-weight: 600; }
    .tablewrap { overflow-x: auto; }
    table.cols { width: 100%; }
    table.cols td, table.cols th { padding: 6px 12px 6px 0; vertical-align: top; }
    .cases { display: grid; grid-template-columns: 1fr; gap: 6px; }
    .case p { margin: 2px 0 10px; }
    .csv { margin: 4px 0; }
    .rules li { margin-bottom: 6px; }
  `],
})
export class ImportsComponent {
  private readonly api = inject(ApiService);

  readonly header = 'uid;date;kind;task_type_code;sda_project_id;jira_issue_id;non_sda_project;minutes;note';
  readonly caseSplit = [
    'XE12345;2026-09-15;;01.01.01;SDATOOL-37040;;;420;',
    'XE12345;2026-09-15;;03.01;;;;60;Formación',
  ].join('\n');
  readonly caseFeature = 'XE12345;2026-09-16;;01.01.02;SDATOOL-37040;ETR-01;;480;';
  readonly caseOff = 'XE12345;2026-09-17;NON_WORKING;;;;;;Vacaciones';

  readonly columns: ColumnHelp[] = [
    { name: 'uid', required: 'Sí', what: 'Identificador de BBVA de la persona, el mismo que en Personas. Da igual mayúsculas o minúsculas.', example: 'XE12345' },
    { name: 'date', required: 'Sí', what: 'El día, en formato año-mes-día.', example: '2026-09-15' },
    { name: 'kind', required: 'No', what: 'Vacío o WORKED para un día trabajado. NON_WORKING para vacaciones, baja o cualquier día sin imputación.', example: 'NON_WORKING' },
    { name: 'task_type_code', required: 'Sí, salvo NON_WORKING', what: 'Código de la actividad del árbol de BBVA. Tiene que ser una actividad final (imputable).', example: '01.01.01' },
    { name: 'sda_project_id', required: 'Según la actividad', what: 'Proyecto SDA. Obligatorio en las actividades de proyectos SDA (las que cuelgan de "01").', example: 'SDATOOL-37040' },
    { name: 'jira_issue_id', required: 'Según la actividad', what: 'Issue de Jira (feature, task o conceptualization time). Solo en actividades que la exigen, y siempre con su SDA.', example: 'ETR-01' },
    { name: 'non_sda_project', required: 'Según la actividad', what: 'Nombre del proyecto no SDA, tal cual aparece en el catálogo. Solo en actividades de proyectos no SDA (las que cuelgan de "02").', example: 'Soporte interno' },
    { name: 'minutes', required: 'Sí, salvo NON_WORKING', what: 'Minutos, no horas. Una jornada de 8 horas son 480. Entre 1 y 1440.', example: '480' },
    { name: 'note', required: 'No', what: 'Texto libre. Se guarda con el día y se ve en el mes de la persona.', example: 'Vacaciones' },
  ] as ColumnHelp[];

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

  /** Plantilla con la cabecera y los tres casos de ejemplo, lista para abrir en Excel y rellenar. */
  downloadTemplate(): void {
    const content = [this.header, this.caseSplit, this.caseFeature, this.caseOff].join('\n') + '\n';
    const blob = new Blob(['﻿' + content], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'plantilla-imputaciones.csv';
    a.click();
    URL.revokeObjectURL(url);
  }
}
