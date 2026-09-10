import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, MatButtonModule, MatTooltipModule],
  template: `
    <mat-toolbar color="primary" class="top">
      <button mat-icon-button (click)="nav.toggle()" aria-label="Menú"><mat-icon>menu</mat-icon></button>
      <span class="title">Imputaciones · Activity Report</span>
      <span class="spacer"></span>
      @if (auth.me(); as me) {
        <span class="user" [matTooltip]="me.email">
          {{ me.displayName }}
          <small>({{ me.role === 'MANAGER' ? 'Gestor' : 'Consultor' }})</small>
        </span>
      }
      <button mat-icon-button (click)="auth.logout()" matTooltip="Cerrar sesión" aria-label="Cerrar sesión">
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>

    <mat-sidenav-container class="container">
      <mat-sidenav #nav mode="side" opened class="sidenav">
        <mat-nav-list>
          @if (auth.personId() != null) {
            <a mat-list-item routerLink="/mi-mes" routerLinkActive="active">
              <mat-icon matListItemIcon>calendar_month</mat-icon><span matListItemTitle>Mi mes</span>
            </a>
          }
          @if (auth.isManager()) {
            <a mat-list-item routerLink="/personas" routerLinkActive="active">
              <mat-icon matListItemIcon>people</mat-icon><span matListItemTitle>Personas</span>
            </a>
            <a mat-list-item routerLink="/envios" routerLinkActive="active">
              <mat-icon matListItemIcon>cloud_upload</mat-icon><span matListItemTitle>Envíos</span>
            </a>
            <a mat-list-item routerLink="/rechazos" routerLinkActive="active">
              <mat-icon matListItemIcon>report</mat-icon><span matListItemTitle>Rechazos</span>
            </a>
            <a mat-list-item routerLink="/importar" routerLinkActive="active">
              <mat-icon matListItemIcon>upload_file</mat-icon><span matListItemTitle>Importar CSV</span>
            </a>
            <a mat-list-item routerLink="/catalogos" routerLinkActive="active">
              <mat-icon matListItemIcon>menu_book</mat-icon><span matListItemTitle>Catálogos</span>
            </a>
            <a mat-list-item routerLink="/festivos" routerLinkActive="active">
              <mat-icon matListItemIcon>event_busy</mat-icon><span matListItemTitle>Festivos</span>
            </a>
            <a mat-list-item routerLink="/usuarios" routerLinkActive="active">
              <mat-icon matListItemIcon>manage_accounts</mat-icon><span matListItemTitle>Usuarios</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content>
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; }
    .top { position: sticky; top: 0; z-index: 10; }
    .title { font-size: 18px; margin-left: 4px; }
    .spacer { flex: 1 1 auto; }
    .user { font-size: 14px; margin-right: 8px; small { opacity: .8; margin-left: 4px; } }
    .container { flex: 1 1 auto; }
    .sidenav { width: 220px; }
    a.active { background: rgba(0, 68, 129, .1); font-weight: 500; }
  `],
})
export class ShellComponent {
  readonly auth = inject(AuthService);
}
