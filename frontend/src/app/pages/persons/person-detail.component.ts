import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ApiService } from '../../core/api.service';
import { CatalogService } from '../../core/catalog.service';
import { NotifyService } from '../../core/notify.service';
import { Person, TaskType, Template } from '../../core/models';
import { confirm } from '../../shared/confirm-dialog.component';
import { HoursPipe, LineLabelPipe } from '../../shared/line-label.pipe';
import { PersonDialogComponent, PersonDialogData } from './person-dialog.component';
import { TemplateDialogComponent, TemplateDialogData } from './template-dialog.component';

@Component({
  selector: 'app-person-detail',
  standalone: true,
  imports: [
    DatePipe, RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatTooltipModule, MatProgressBarModule,
    MatDialogModule, HoursPipe, LineLabelPipe,
  ],
  template: `
    <div class="page">
      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
      @if (person(); as p) {
        <div class="page-header">
          <a mat-icon-button routerLink="/personas" aria-label="Volver"><mat-icon>arrow_back</mat-icon></a>
          <h1>{{ p.displayName }} <span class="muted small">{{ p.uid }}</span></h1>
          @if (!p.active) { <span class="chip-day weekend">Baja</span> }
          <span class="spacer"></span>
          <button mat-stroked-button (click)="editPerson()"><mat-icon>edit</mat-icon> Editar</button>
          <a mat-flat-button color="primary" [routerLink]="['/personas', p.id, 'mes']"><mat-icon>calendar_month</mat-icon> Ver mes</a>
        </div>

        <mat-card class="card">
          <mat-card-content>
            <div class="grid">
              <div><span class="muted small">Email</span><div>{{ p.email }}</div></div>
              <div><span class="muted small">Geografía</span><div>{{ p.geography }}</div></div>
              <div><span class="muted small">Código GPS</span><div class="mono">{{ p.gpsCode ?? '—' }}</div></div>
              <div><span class="muted small">Person ID (AR)</span><div class="mono">{{ p.personId ?? '—' }}</div></div>
              <div><span class="muted small">Alta</span><div>{{ p.startDate | date: 'dd/MM/yyyy' }}</div></div>
              <div><span class="muted small">Baja</span><div>{{ p.endDate ? (p.endDate | date: 'dd/MM/yyyy') : '—' }}</div></div>
            </div>
          </mat-card-content>
        </mat-card>

        <div class="page-header">
          <h1 style="font-size: 18px">Plantillas</h1>
          <span class="spacer"></span>
          <button mat-flat-button color="primary" (click)="newTemplate()"><mat-icon>add</mat-icon> Nueva plantilla</button>
        </div>
        <p class="hint">Cada plantilla define las líneas que se imputan por defecto en los días laborables de su periodo de validez.</p>

        @for (t of templates(); track t.id) {
          <mat-card class="card">
            <mat-card-header>
              <mat-card-title style="font-size: 15px">
                Desde {{ t.validFrom | date: 'dd/MM/yyyy' }}
                @if (t.validTo) { hasta {{ t.validTo | date: 'dd/MM/yyyy' }} } @else { <span class="muted">(sin fin)</span> }
                · {{ total(t) | hours }}
              </mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <ul class="lines">
                @for (l of t.lines; track $index) { <li>{{ l | lineLabel: taskTypes() }}</li> }
              </ul>
            </mat-card-content>
            <mat-card-actions align="end">
              <button mat-button (click)="newTemplate(t)" matTooltip="Crear una plantilla nueva partiendo de estas líneas"><mat-icon>content_copy</mat-icon> Duplicar</button>
              <button mat-button color="warn" (click)="deleteTemplate(t)"><mat-icon>delete</mat-icon> Eliminar</button>
            </mat-card-actions>
          </mat-card>
        } @empty {
          <div class="info-box">Esta persona no tiene plantillas: sus días laborables aparecerán como huecos hasta que se cree una o se imputen manualmente.</div>
        }
      }
    </div>
  `,
  styles: [`
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
    ul.lines { margin: 0; padding-left: 18px; font-size: 13px; }
  `],
})
export class PersonDetailComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly catalogs = inject(CatalogService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  @Input({ required: true }) id!: string;

  readonly person = signal<Person | null>(null);
  readonly templates = signal<Template[]>([]);
  readonly taskTypes = signal<Map<string, TaskType> | null>(null);
  readonly loading = signal(false);

  get personId(): number {
    return Number(this.id);
  }

  ngOnInit(): void {
    this.catalogs.taskTypes().subscribe({
      next: (list) => this.taskTypes.set(new Map(list.map((t) => [t.code, t]))),
      error: () => this.taskTypes.set(new Map()),
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.person(this.personId).subscribe({
      next: (p) => { this.person.set(p); this.loading.set(false); },
      error: (err: unknown) => { this.loading.set(false); this.notify.error(err); },
    });
    this.api.templates(this.personId).subscribe({
      next: (list) => this.templates.set(list.slice().sort((a, b) => b.validFrom.localeCompare(a.validFrom))),
      error: (err: unknown) => this.notify.error(err),
    });
  }

  total(t: Template): number {
    return t.lines.reduce((a, l) => a + l.minutes, 0);
  }

  editPerson(): void {
    this.dialog
      .open<PersonDialogComponent, PersonDialogData, Person | null>(PersonDialogComponent, { data: { person: this.person() }, width: '640px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((saved) => { if (saved) { this.notify.ok('Persona actualizada'); this.load(); } });
  }

  newTemplate(from?: Template): void {
    this.dialog
      .open<TemplateDialogComponent, TemplateDialogData, Template | null>(TemplateDialogComponent, {
        data: { personId: this.personId, initialLines: from?.lines },
        width: '860px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .subscribe((created) => { if (created) { this.notify.ok('Plantilla creada'); this.load(); } });
  }

  deleteTemplate(t: Template): void {
    confirm(this.dialog, {
      title: 'Eliminar plantilla',
      message: `¿Eliminar la plantilla válida desde ${t.validFrom}? Los días que dependan de ella quedarán sin imputación por defecto.`,
      confirmLabel: 'Eliminar',
      warn: true,
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.deleteTemplate(this.personId, t.id).subscribe({
        next: () => { this.notify.ok('Plantilla eliminada'); this.load(); },
        error: (err: unknown) => this.notify.error(err),
      });
    });
  }
}
