import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { forkJoin, of, catchError } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { Person, User } from '../../core/models';
import { UserDialogComponent, UserDialogData } from './user-dialog.component';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatTableModule, MatTooltipModule, MatProgressBarModule, MatDialogModule],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Usuarios del panel</h1>
        <span class="spacer"></span>
        <button mat-flat-button color="primary" (click)="openDialog(null)"><mat-icon>person_add</mat-icon> Nuevo usuario</button>
      </div>
      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }

      <div class="table-wrap">
        <table mat-table [dataSource]="users()">
          <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>Email</th><td mat-cell *matCellDef="let u">{{ u.email }}</td></ng-container>
          <ng-container matColumnDef="displayName"><th mat-header-cell *matHeaderCellDef>Nombre</th><td mat-cell *matCellDef="let u">{{ u.displayName }}</td></ng-container>
          <ng-container matColumnDef="role"><th mat-header-cell *matHeaderCellDef>Rol</th><td mat-cell *matCellDef="let u">{{ u.role === 'MANAGER' ? 'Gestor' : 'Consultor' }}</td></ng-container>
          <ng-container matColumnDef="person"><th mat-header-cell *matHeaderCellDef>Persona</th><td mat-cell *matCellDef="let u">{{ personLabel(u) }}</td></ng-container>
          <ng-container matColumnDef="active">
            <th mat-header-cell *matHeaderCellDef>Activo</th>
            <td mat-cell *matCellDef="let u"><mat-icon [class]="u.active ? 'ok-text' : 'muted'">{{ u.active ? 'check_circle' : 'cancel' }}</mat-icon></td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let u"><button mat-icon-button (click)="openDialog(u)" matTooltip="Editar" aria-label="Editar"><mat-icon>edit</mat-icon></button></td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
          <tr class="mat-row" *matNoDataRow><td class="mat-cell muted" [attr.colspan]="columns.length" style="padding: 16px">Sin usuarios</td></tr>
        </table>
      </div>
    </div>
  `,
})
export class UsersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);

  readonly columns = ['email', 'displayName', 'role', 'person', 'active', 'actions'];
  readonly users = signal<User[]>([]);
  readonly loading = signal(false);
  private persons = new Map<number, Person>();

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({
      users: this.api.users(),
      persons: this.api.persons().pipe(catchError(() => of([] as Person[]))),
    }).subscribe({
      next: ({ users, persons }) => {
        this.persons = new Map(persons.map((p) => [p.id, p]));
        this.users.set(users.slice().sort((a, b) => a.email.localeCompare(b.email)));
        this.loading.set(false);
      },
      error: (e: unknown) => { this.loading.set(false); this.notify.error(e); },
    });
  }

  personLabel(u: User): string {
    if (u.personId == null) return '';
    const p = this.persons.get(u.personId);
    return p ? `${p.displayName} (${p.uid})` : `#${u.personId}`;
  }

  openDialog(user: User | null): void {
    this.dialog
      .open<UserDialogComponent, UserDialogData, User | null>(UserDialogComponent, { data: { user }, width: '560px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((saved) => { if (saved) { this.notify.ok(user ? 'Usuario actualizado' : 'Usuario creado'); this.load(); } });
  }
}
