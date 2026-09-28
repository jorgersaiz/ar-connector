/**
 * Códigos de rechazo de Activity Report, según el documento de BBVA
 * "[API Vendors] Códigos de error / rechazos", en lenguaje de usuario.
 */
export const REJECT_CODES: Record<string, string> = {
  APV_01: 'Fecha no válida: es festivo, fin de semana o está fuera del periodo modificable.',
  APV_02: 'Persona no válida: no pertenece al proveedor, no está en perímetro, no existe, no está registrada o su fecha de inicio es posterior al día imputado.',
  APV_03: 'Actividad no válida: los cinco niveles no forman un camino del árbol hasta una actividad final visible.',
  APV_04: 'Proyecto SDA no válido: falta, no existe o no admite imputación por su estado y fechas.',
  APV_04_EV: 'Proyecto SDA de evolutivo en una actividad que no es de evolutivo, o al revés.',
  APV_05: 'Issue de Jira no válida: falta, no existe, no pertenece al proyecto SDA indicado o no admite imputación por su estado y fechas.',
  APV_05_EV: 'Issue de evolutivo en una actividad que no es de evolutivo, o al revés.',
  APV_05_CT: 'Issue de Conceptualization Time en una actividad que no lo es, o al revés.',
  APV_05_EV_CT: 'Issue de Conceptualization Time en una actividad que no lo es, o al revés.',
  APV_06: 'Proyecto no SDA no válido: falta, lleva un código SDA o se ha informado un SDA sin necesidad.',
  APV_07: 'Detalle de tecnología no informado.',
  APV_08: 'Tiempo no válido: falta, es menor que 0, mayor que 1440 minutos o no es un número.',
  APV_09: 'Error no controlado en Activity Report. Casi siempre son líneas duplicadas en el fichero; si no, hay que contactar con el equipo de AR.',
  APV_10: 'Actividad oculta: esa persona no puede imputar a esa actividad ese día.',
};

export function rejectCodeText(code: string | null | undefined): string {
  if (!code) return '';
  return REJECT_CODES[code] ?? 'Código de rechazo de Activity Report sin descripción conocida.';
}
