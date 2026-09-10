import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.load().pipe(
    map((me) => (me ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }))),
  );
};

export const managerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.load().pipe(
    map((me) => {
      if (!me) return router.createUrlTree(['/login']);
      return me.role === 'MANAGER' ? true : router.createUrlTree([auth.defaultRoute()]);
    }),
  );
};

/** Ruta raíz: manda a /mi-mes o /personas según el usuario. */
export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.load().pipe(map(() => router.createUrlTree([auth.defaultRoute()])));
};
