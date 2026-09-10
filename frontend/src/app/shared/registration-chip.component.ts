import { Component, Input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Person } from '../core/models';

/** Significado de los códigos de rechazo APV_UR_xx del feedback de altas. */
export const REJECT_REASON: Record<string, string> = {
  APV_UR_01: 'No está en el directorio o no tiene email',
  APV_UR_02: 'No pertenece al vendor o es interno',
  APV_UR_03: 'No cumple las reglas de acceso',
  APV_UR_04: 'Geografía no detectada o no es del vendor',
  APV_UR_05: 'Ya está registrado',
  APV_UR_06: 'Fecha de incorporación posterior a hoy',
};

export function rejectReasonText(code: string | null | undefined): string {
  if (!code) return 'Rechazada (sin motivo)';
  const meaning = REJECT_REASON[code];
  return meaning ? `${code}: ${meaning}` : code;
}

/** Chip con el estado del alta en Activity Report de una persona. Para NOT_REQUIRED pinta solo "—". */
@Component({
  selector: 'app-registration-chip',
  standalone: true,
  imports: [DatePipe, MatTooltipModule],
  template: `
    @switch (person.registrationStatus) {
      @case ('PENDING') { <span class="chip-reg pending">Pendiente de envío</span> }
      @case ('SENT') { <span class="chip-reg sent">Enviada{{ person.registrationSentDate ? ' ' + (person.registrationSentDate | date: 'dd/MM') : '' }}</span> }
      @case ('REGISTERED') { <span class="chip-reg registered">Registrada</span> }
      @case ('REJECTED') { <span class="chip-reg rejected" [matTooltip]="reasonText()">Rechazada{{ person.registrationReason ? ' ' + person.registrationReason : '' }}</span> }
      @default { <span class="muted">—</span> }
    }
  `,
  styles: [`
    .chip-reg {
      display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 12px; line-height: 18px; white-space: nowrap;
      background: #e7e9ee; color: #333;
      &.pending { background: #fff3e0; color: #e65100; }
      &.sent { background: #e3f2fd; color: #0d47a1; }
      &.registered { background: #e8f5e9; color: #1b5e20; }
      &.rejected { background: #fdecea; color: #b3261e; cursor: help; }
    }
  `],
})
export class RegistrationChipComponent {
  @Input({ required: true }) person!: Person;

  reasonText(): string {
    return rejectReasonText(this.person.registrationReason);
  }
}
