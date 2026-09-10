import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Observable } from 'rxjs';

export interface ConfirmData {
  title: string;
  message: string;
  confirmLabel?: string;
  warn?: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content><p style="white-space: pre-line">{{ data.message }}</p></mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(false)">Cancelar</button>
      <button mat-flat-button [color]="data.warn ? 'warn' : 'primary'" (click)="ref.close(true)">
        {{ data.confirmLabel ?? 'Confirmar' }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialogComponent {
  readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<ConfirmDialogComponent, boolean>);
}

/** Abre un diálogo de confirmación y devuelve `true` si el usuario confirma. */
export function confirm(dialog: MatDialog, data: ConfirmData): Observable<boolean | undefined> {
  return dialog.open<ConfirmDialogComponent, ConfirmData, boolean>(ConfirmDialogComponent, { data, width: '420px' }).afterClosed();
}
