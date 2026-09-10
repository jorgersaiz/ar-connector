import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { errorText } from './errors';

@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snack = inject(MatSnackBar);

  ok(message: string): void {
    this.snack.open(message, 'OK', { duration: 3500 });
  }

  error(err: unknown): void {
    this.snack.open(errorText(err), 'Cerrar', { duration: 8000, panelClass: 'snack-error' });
  }
}
