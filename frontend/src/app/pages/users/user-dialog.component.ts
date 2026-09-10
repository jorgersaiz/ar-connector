import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../core/api.service';
import { Person, Role, User } from '../../core/models';
import { errorLines } from '../../core/errors';

export interface UserDialogData {
  user: User | null;
}

@Component({
  selector: 'app-user-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title>{{ data.user ? 'Editar usuario' : 'Nuevo usuario' }}</h2>
    @if (saving()) { <mat-progress-bar mode="indeterminate" /> }
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field>
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="off" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Nombre</mat-label>
          <input matInput formControlName="displayName" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>{{ data.user ? 'Nueva contraseña (vacío = no cambiar)' : 'Contraseña' }}</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="new-password" />
        </mat-form-field>
        <div class="row">
          <mat-form-field>
            <mat-label>Rol</mat-label>
            <mat-select formControlName="role">
              <mat-option value="MANAGER">Gestor</mat-option>
              <mat-option value="CONSULTANT">Consultor</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Persona asociada</mat-label>
            <mat-select formControlName="personId">
              <mat-option [value]="null">— Ninguna —</mat-option>
              @for (p of persons(); track p.id) { <mat-option [value]="p.id">{{ p.displayName }} ({{ p.uid }})</mat-option> }
            </mat-select>
            <mat-hint>Necesaria para que el usuario vea "Mi mes"</mat-hint>
          </mat-form-field>
        </div>
        @if (data.user) {
          <mat-slide-toggle formControlName="active">Activo</mat-slide-toggle>
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
export class UserDialogComponent implements OnInit {
  readonly data = inject<UserDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<UserDialogComponent, User | null>);
  private readonly api = inject(ApiService);

  readonly persons = signal<Person[]>([]);
  readonly saving = signal(false);
  readonly errors = signal<string[]>([]);
  readonly form = new FormGroup({
    email: new FormControl(this.data.user?.email ?? '', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    displayName: new FormControl(this.data.user?.displayName ?? '', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: this.data.user ? [] : [Validators.required, Validators.minLength(8)] }),
    role: new FormControl<Role>(this.data.user?.role ?? 'CONSULTANT', { nonNullable: true }),
    personId: new FormControl<number | null>(this.data.user?.personId ?? null),
    active: new FormControl<boolean>(this.data.user?.active ?? true, { nonNullable: true }),
  });

  ngOnInit(): void {
    this.api.persons(true).subscribe({
      next: (list) => {
        const sorted = list.slice().sort((a, b) => a.displayName.localeCompare(b.displayName));
        // Si el usuario está vinculado a una persona inactiva, la mantenemos en la lista para no perder el vínculo.
        const current = this.data.user?.personId;
        if (current != null && !sorted.some((p) => p.id === current)) {
          this.api.person(current).subscribe({ next: (p) => this.persons.set([p, ...sorted]), error: () => this.persons.set(sorted) });
        } else {
          this.persons.set(sorted);
        }
      },
      error: () => this.persons.set([]),
    });
  }

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errors.set([]);
    const req = this.data.user
      ? this.api.updateUser(this.data.user.id, {
          email: v.email.trim(),
          displayName: v.displayName.trim(),
          role: v.role,
          personId: v.personId,
          active: v.active,
          ...(v.password ? { password: v.password } : {}),
        })
      : this.api.createUser({ email: v.email.trim(), password: v.password, displayName: v.displayName.trim(), role: v.role, personId: v.personId });
    req.subscribe({
      next: (u) => { this.saving.set(false); this.ref.close(u); },
      error: (err: unknown) => { this.saving.set(false); this.errors.set(errorLines(err)); },
    });
  }
}
