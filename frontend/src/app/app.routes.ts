import { Routes } from '@angular/router';
import { authGuard, homeRedirectGuard, managerGuard } from './core/guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent) },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', canActivate: [homeRedirectGuard], children: [] },
      { path: 'mi-mes', loadComponent: () => import('./pages/month/month.component').then((m) => m.MonthComponent) },
      { path: 'situacion', canActivate: [managerGuard], loadComponent: () => import('./pages/dashboard/dashboard.component').then((m) => m.DashboardComponent) },
      {
        path: 'personas',
        canActivate: [managerGuard],
        children: [
          { path: '', loadComponent: () => import('./pages/persons/persons.component').then((m) => m.PersonsComponent) },
          { path: ':id', loadComponent: () => import('./pages/persons/person-detail.component').then((m) => m.PersonDetailComponent) },
          { path: ':id/mes', loadComponent: () => import('./pages/month/month.component').then((m) => m.MonthComponent) },
        ],
      },
      { path: 'envios', canActivate: [managerGuard], loadComponent: () => import('./pages/uploads/uploads.component').then((m) => m.UploadsComponent) },
      { path: 'rechazos', canActivate: [managerGuard], loadComponent: () => import('./pages/feedback/feedback.component').then((m) => m.FeedbackComponent) },
      { path: 'importar', canActivate: [managerGuard], loadComponent: () => import('./pages/imports/imports.component').then((m) => m.ImportsComponent) },
      { path: 'catalogos', canActivate: [managerGuard], loadComponent: () => import('./pages/catalogs/catalogs.component').then((m) => m.CatalogsComponent) },
      { path: 'festivos', canActivate: [managerGuard], loadComponent: () => import('./pages/holidays/holidays.component').then((m) => m.HolidaysComponent) },
      { path: 'usuarios', canActivate: [managerGuard], loadComponent: () => import('./pages/users/users.component').then((m) => m.UsersComponent) },
    ],
  },
  { path: '**', redirectTo: '' },
];
