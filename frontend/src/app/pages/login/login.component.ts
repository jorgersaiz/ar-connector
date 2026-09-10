import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/errors';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressBarModule],
  template: `
    <div class="wrap">
      <mat-card class="login">
        <mat-card-header>
          <mat-card-title>Imputaciones · Activity Report</mat-card-title>
          <mat-card-subtitle>Inicia sesión con tu cuenta del panel</mat-card-subtitle>
        </mat-card-header>
        @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()" class="dialog-form">
            <mat-form-field>
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="username" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Contraseña</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="current-password" />
            </mat-form-field>
            @if (error(); as e) { <div class="errors-box">{{ e }}</div> }
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || loading()">Entrar</button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #eef1f5; }
    .login { width: 380px; max-width: 92vw; }
    form { padding-top: 16px; }
  `],
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  submit(): void {
    if (this.form.invalid) return;
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        this.loading.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        this.router.navigateByUrl(returnUrl && returnUrl !== '/login' ? returnUrl : this.auth.defaultRoute());
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const e = apiError(err);
        this.error.set(e.status === 401 ? 'Credenciales incorrectas' : e.message);
      },
    });
  }
}
