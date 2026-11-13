import { Component, OnInit, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ApiService } from '../../core/api.service';
import { PersonImportAction, PersonImportResult } from '../../core/models';
import { errorLines } from '../../core/errors';

interface ColumnHelp {
  name: string;
  required: string;
  what: string;
  example: string;
}

const ACTION_LABEL: Record<PersonImportAction, string> = {
  CREAR: 'Nueva',
  ACTUALIZAR: 'Se actualiza',
  SIN_CAMBIOS: 'Sin cambios',
};

/**
 * Alta masiva de personas desde CSV. Dos pasos: "Comprobar" enseña qué se va a
 * crear o cambiar sin guardar nada, e "Importar" lo guarda.
 */
@Component({
  selector: 'app-person-import',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatTableModule],
  template: `
    <mat-card class="card">
      <mat-card-content>
        <div class="toolbar-row">
          <input #fileInput type="file" accept=".csv,text/csv" (change)="onFile(fileInput.files); fileInput.value = ''" hidden />
          <button mat-stroked-button (click)="fileInput.click()" [disabled]="busy()"><mat-icon>attach_file</mat-icon> Elegir fichero</button>
          <span class="muted">{{ file()?.name ?? 'Ningún fichero seleccionado' }}</span>
          <span class="spacer"></span>
          <button mat-stroked-button (click)="downloadTemplate()"><mat-icon>download</mat-icon> Descargar plantilla</button>
          <button mat-stroked-button (click)="check()" [disabled]="!file() || busy()"><mat-icon>fact_check</mat-icon> Comprobar</button>
          <button mat-flat-button color="primary" (click)="save()" [disabled]="!checked() || busy()"><mat-icon>upload</mat-icon> Importar</button>
        </div>
        @if (busy()) { <mat-progress-bar mode="indeterminate" /> }

        @if (result(); as r) {
          <div [class]="r.dryRun ? 'info-box' : 'ok-box'">
            @if (r.dryRun) {
              <b>Comprobado, todavía no se ha guardado nada.</b> Si es correcto, pulsa "Importar".
            } @else {
              <b>Importación completada.</b>
            }
            {{ r.created }} {{ r.created === 1 ? 'persona nueva' : 'personas nuevas' }},
            {{ r.updated }} {{ r.updated === 1 ? 'actualizada' : 'actualizadas' }},
            {{ r.unchanged }} sin cambios.
            @if (r.registrationsRequested) {
              Se {{ r.dryRun ? 'pedirá' : 'ha pedido' }} el alta en Activity Report de {{ r.registrationsRequested }}
              ({{ r.registrationsRequested === 1 ? 'se envía' : 'se envían' }} en el próximo envío).
            }
          </div>
          <div class="tablewrap">
            <table mat-table [dataSource]="r.rows" class="rows">
              <ng-container matColumnDef="line"><th mat-header-cell *matHeaderCellDef>Línea</th><td mat-cell *matCellDef="let x">{{ x.line }}</td></ng-container>
              <ng-container matColumnDef="uid"><th mat-header-cell *matHeaderCellDef>UID</th><td mat-cell *matCellDef="let x" class="mono">{{ x.uid }}</td></ng-container>
              <ng-container matColumnDef="displayName"><th mat-header-cell *matHeaderCellDef>Nombre</th><td mat-cell *matCellDef="let x">{{ x.displayName }}</td></ng-container>
              <ng-container matColumnDef="action">
                <th mat-header-cell *matHeaderCellDef>Qué pasa</th>
                <td mat-cell *matCellDef="let x"><span class="chip-day" [class.holiday]="x.action === 'CREAR'" [class.manual]="x.action === 'ACTUALIZAR'">{{ actionLabel(x.action) }}</span></td>
              </ng-container>
              <ng-container matColumnDef="changes">
                <th mat-header-cell *matHeaderCellDef>Detalle</th>
                <td mat-cell *matCellDef="let x" class="small">@for (c of x.changes; track $index) { <div>{{ c }}</div> }</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="rowColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: rowColumns"></tr>
            </table>
          </div>
        }
        @if (errors().length) {
          <div class="errors-box">
            <strong>No se puede importar. No se ha guardado nada; corrige estas filas y vuelve a probar.</strong>
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
          Sirve para dar de alta de golpe a las personas del equipo, o para corregir datos de muchas a la vez.
          Se puede preparar en Excel y guardar como "CSV (delimitado por punto y coma)"; las tildes y las fechas
          en formato 01/11/2026 se entienden bien. Después de importar, cada persona necesita su plantilla por defecto
          para que se impute sola.
        </p>

        <h3>Las columnas</h3>
        <div class="tablewrap">
          <table mat-table [dataSource]="columns" class="cols">
            <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Columna</th><td mat-cell *matCellDef="let c"><code>{{ c.name }}</code></td></ng-container>
            <ng-container matColumnDef="required"><th mat-header-cell *matHeaderCellDef>Obligatoria</th><td mat-cell *matCellDef="let c">{{ c.required }}</td></ng-container>
            <ng-container matColumnDef="what"><th mat-header-cell *matHeaderCellDef>Qué es</th><td mat-cell *matCellDef="let c">{{ c.what }}</td></ng-container>
            <ng-container matColumnDef="example"><th mat-header-cell *matHeaderCellDef>Ejemplo</th><td mat-cell *matCellDef="let c"><code>{{ c.example }}</code></td></ng-container>
            <tr mat-header-row *matHeaderRowDef="['name', 'required', 'what', 'example']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['name', 'required', 'what', 'example']"></tr>
          </table>
        </div>

        <h3>Ejemplo</h3>
        <pre class="csv">{{ example }}</pre>

        <h3>Reglas que conviene saber</h3>
        <ul class="rules">
          <li><b>Primero "Comprobar".</b> Enseña persona a persona si es nueva, qué datos le cambian o si se queda igual, sin guardar nada.</li>
          <li><b>Si el uid ya existe, se actualiza</b> con los datos del fichero. Las columnas opcionales vacías conservan lo que hubiera: reimportar el mismo fichero no borra una baja hecha desde el panel.</li>
          <li><b>Es todo o nada.</b> Si una fila falla, no se guarda ninguna y se indica el número de línea y el motivo.</li>
          <li><b>needs_registration = si</b> solo para quien nunca ha tenido acceso a Activity Report. Quien ya imputa en AR no necesita alta: BBVA la rechazaría.</li>
          <li>Desde aquí no se dan bajas: para eso está el botón de baja en Personas, o la columna <code>end_date</code>.</li>
        </ul>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    code { background: #eee; padding: 0 4px; border-radius: 3px; white-space: nowrap; }
    .title-icon { vertical-align: middle; margin-right: 6px; }
    h3 { margin: 20px 0 8px; font-size: 15px; font-weight: 600; }
    .tablewrap { overflow-x: auto; margin-top: 8px; }
    table.cols, table.rows { width: 100%; }
    table.cols td, table.cols th { padding: 6px 12px 6px 0; vertical-align: top; }
    table.rows td { vertical-align: top; padding-top: 6px; padding-bottom: 6px; }
    .rules li { margin-bottom: 6px; }
    .info-box { background: #e3f2fd; border-left: 4px solid #1976d2; padding: 10px 12px; margin-top: 12px; border-radius: 4px; }
  `],
})
export class PersonImportComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly header = 'uid;display_name;email;start_date;end_date;geography;gps_code;needs_registration';
  readonly rowColumns = ['line', 'uid', 'displayName', 'action', 'changes'];

  readonly file = signal<File | null>(null);
  readonly busy = signal(false);
  readonly result = signal<PersonImportResult | null>(null);
  readonly errors = signal<string[]>([]);
  /** El fichero actual ya se comprobó sin errores: se puede importar. */
  readonly checked = signal(false);
  /** Valores de la empresa (para la ayuda y la plantilla). */
  readonly gps = signal('0000417143');
  readonly geography = signal('ES');

  example = '';
  columns: ColumnHelp[] = [];

  ngOnInit(): void {
    this.buildHelp();
    this.api.config().subscribe({
      next: (c) => {
        if (c.gpsCodes.length === 1) this.gps.set(c.gpsCodes[0]);
        if (c.geographies.length === 1) this.geography.set(c.geographies[0]);
        this.buildHelp();
      },
      error: () => { /* la ayuda se queda con los valores de ejemplo */ },
    });
  }

  actionLabel(a: PersonImportAction): string {
    return ACTION_LABEL[a];
  }

  onFile(files: FileList | null): void {
    this.file.set(files && files.length ? files[0] : null);
    this.result.set(null);
    this.errors.set([]);
    this.checked.set(false);
  }

  check(): void {
    this.send(true);
  }

  save(): void {
    this.send(false);
  }

  private send(dryRun: boolean): void {
    const f = this.file();
    if (!f) return;
    this.busy.set(true);
    this.errors.set([]);
    this.api.importPersons(f, dryRun).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.result.set(r);
        this.checked.set(dryRun);
        if (!dryRun) this.file.set(null);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.result.set(null);
        this.checked.set(false);
        this.errors.set(errorLines(err));
      },
    });
  }

  /** Plantilla con la cabecera y dos filas de ejemplo, lista para abrir en Excel y rellenar. */
  downloadTemplate(): void {
    const blob = new Blob(['﻿' + this.example + '\n'], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'plantilla-personas.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  private buildHelp(): void {
    const gps = this.gps();
    const geo = this.geography();
    this.example = [
      this.header,
      'XE123456;Ana López García;ana.lopez@arktic.com;2026-11-01;;;;no',
      `XE654321;Bruno Pérez;bruno.perez@arktic.com;15/11/2026;;${geo};${gps};si`,
    ].join('\n');
    this.columns = [
      { name: 'uid', required: 'Sí', what: 'Usuario de BBVA de la persona. Da igual mayúsculas o minúsculas. Si ya existe en Personas, se actualiza.', example: 'XE123456' },
      { name: 'display_name', required: 'Sí', what: 'Nombre y apellidos, como se verá en el panel.', example: 'Ana López García' },
      { name: 'email', required: 'Sí', what: 'Correo de la persona; es donde le llegarán los avisos.', example: 'ana.lopez@arktic.com' },
      { name: 'start_date', required: 'Sí', what: 'Primer día que se le imputa.', example: '2026-11-01 o 01/11/2026' },
      { name: 'end_date', required: 'No', what: 'Último día que se le imputa, si ya se sabe. Vacío: sin fecha de baja (o se conserva la que tuviera).', example: '2027-06-30' },
      { name: 'geography', required: 'No', what: `Geografía de la persona; marca su calendario de festivos. Vacío: ${geo}.`, example: geo },
      { name: 'gps_code', required: 'No', what: `Código GPS de la empresa en BBVA. Vacío: ${gps} (Arktic).`, example: gps },
      { name: 'needs_registration', required: 'No', what: '"si" si la persona nunca ha tenido acceso a Activity Report y hay que darla de alta. Vacío o "no": no se pide.', example: 'si' },
    ];
  }
}
