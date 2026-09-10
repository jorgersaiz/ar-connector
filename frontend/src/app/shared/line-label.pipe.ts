import { Pipe, PipeTransform } from '@angular/core';
import { Line, TaskType } from '../core/models';
import { minutesToHours } from '../core/dates';

/** "Ruta del tipo de tarea · proyecto/issue · 4h". Recibe el mapa de tipos de tarea por código. */
@Pipe({ name: 'lineLabel', standalone: true })
export class LineLabelPipe implements PipeTransform {
  transform(line: Line, taskTypes: Map<string, TaskType> | null | undefined, withMinutes = true): string {
    const tt = taskTypes?.get(line.taskTypeCode);
    const parts: string[] = [tt ? tt.path : line.taskTypeCode];
    const target = [line.sdaProjectId, line.jiraIssueId, line.nonSdaProject].filter((s) => !!s).join(' / ');
    if (target) parts.push(target);
    if (withMinutes) parts.push(minutesToHours(line.minutes));
    return parts.join(' · ');
  }
}

@Pipe({ name: 'hours', standalone: true })
export class HoursPipe implements PipeTransform {
  transform(minutes: number | null | undefined): string {
    return minutesToHours(minutes);
  }
}
