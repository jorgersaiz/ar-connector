import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Notification, NotificationKind } from '../../core/models';

export const NOTIFICATION_KIND_LABEL: Record<NotificationKind, string> = {
  GAPS_REMINDER: 'Recordatorio de huecos',
  REJECTIONS: 'Rechazos',
  MANAGER_SUMMARY: 'Resumen para managers',
};

export function notificationKindLabel(kind: NotificationKind): string {
  return NOTIFICATION_KIND_LABEL[kind] ?? kind;
}

/** Muestra el cuerpo completo de un aviso enviado. */
@Component({
  selector: 'app-notification-dialog',
  standalone: true,
  imports: [DatePipe, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ n.subject }}</h2>
    <mat-dialog-content>
      <div class="meta small muted">
        <div><strong>Tipo:</strong> {{ kindLabel }}</div>
        <div><strong>Enviado:</strong> {{ n.sentAt | date: 'dd/MM/yyyy HH:mm' }} · canal {{ n.channel }}</div>
        <div><strong>Destinatario:</strong> {{ n.recipientEmail }}@if (n.personDisplayName) { ({{ n.personDisplayName }}) }</div>
        @if (n.deadline) { <div><strong>Fecha límite:</strong> {{ n.deadline | date: 'dd/MM/yyyy' }}</div> }
      </div>
      <pre class="body">{{ n.body }}</pre>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button color="primary" (click)="ref.close()">Cerrar</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .meta { margin-bottom: 12px; line-height: 1.6; }
    pre.body {
      background: #f5f6f8;
      border-radius: 6px;
      padding: 12px;
      margin: 0;
      font-size: 13px;
      font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
      white-space: pre-wrap;
      word-break: break-word;
      max-height: 60vh;
      overflow: auto;
    }
  `],
})
export class NotificationDialogComponent {
  readonly n = inject<Notification>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<NotificationDialogComponent>);
  readonly kindLabel = notificationKindLabel(this.n.kind);
}
