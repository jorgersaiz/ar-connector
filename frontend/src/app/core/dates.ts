/** Utilidades de fecha: la API usa strings ISO `yyyy-MM-dd`; MatDatepicker usa Date. */

export function toIso(d: Date | string | null | undefined): string | null {
  if (!d) return null;
  if (typeof d === 'string') return d.length >= 10 ? d.substring(0, 10) : d;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function fromIso(s: string | null | undefined): Date | null {
  if (!s) return null;
  const [y, m, d] = s.substring(0, 10).split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d);
}

export function todayIso(): string {
  return toIso(new Date())!;
}

export function minutesToHours(min: number | null | undefined): string {
  const m = min ?? 0;
  if (m === 0) return '0h';
  const h = Math.floor(m / 60);
  const r = m % 60;
  if (h === 0) return `${r}m`;
  return r === 0 ? `${h}h` : `${h}h ${String(r).padStart(2, '0')}m`;
}

const WEEKDAYS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];

export function weekdayShort(iso: string): string {
  const d = fromIso(iso);
  return d ? WEEKDAYS[d.getDay()] : '';
}
