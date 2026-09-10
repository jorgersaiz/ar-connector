import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap } from 'rxjs';
import { ApiService } from './api.service';
import { Me } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  /** Usuario autenticado (null = no autenticado; undefined = aún no comprobado). */
  readonly me = signal<Me | null | undefined>(undefined);
  readonly isManager = computed(() => this.me()?.role === 'MANAGER');
  readonly personId = computed(() => this.me()?.personId ?? null);

  /** Carga `me` una sola vez; devuelve null si no hay sesión. */
  load(): Observable<Me | null> {
    const current = this.me();
    if (current !== undefined) return of(current);
    return this.api.me().pipe(
      tap((me) => this.me.set(me)),
      catchError(() => {
        this.me.set(null);
        return of(null);
      }),
    );
  }

  login(email: string, password: string): Observable<Me> {
    return this.api.login(email, password).pipe(tap((me) => this.me.set(me)));
  }

  logout(): void {
    this.api.logout().pipe(catchError(() => of(void 0))).subscribe(() => {
      this.me.set(null);
      this.router.navigateByUrl('/login');
    });
  }

  /** Marca la sesión como perdida (la usa el interceptor en 401). */
  sessionLost(): void {
    this.me.set(null);
  }

  defaultRoute(): string {
    const me = this.me();
    if (me?.personId != null) return '/mi-mes';
    if (me?.role === 'MANAGER') return '/personas';
    return '/login';
  }
}
