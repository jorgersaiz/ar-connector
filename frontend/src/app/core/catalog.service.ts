import { Injectable, inject } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';
import { ApiService } from './api.service';
import { JiraIssue, NonSdaProject, SdaProject, TaskType } from './models';

/**
 * Catálogos para los selectores.
 *
 * Tipos de tarea (~600) y proyectos no SDA (~1.400) se cargan una vez por
 * sesión y se filtran en el navegador. Proyectos SDA (~11.000) e issues de
 * Jira (~270.000) se buscan en el servidor mientras se escribe.
 */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly api = inject(ApiService);

  private taskTypes$?: Observable<TaskType[]>;
  private selectableTaskTypes$?: Observable<TaskType[]>;
  private nonSdaProjects$?: Observable<NonSdaProject[]>;

  taskTypes(): Observable<TaskType[]> {
    this.taskTypes$ ??= this.api.taskTypes().pipe(shareReplay(1));
    return this.taskTypes$;
  }

  /** Solo los tipos de tarea imputables (`canBeSelected && enabled`). */
  selectableTaskTypes(): Observable<TaskType[]> {
    this.selectableTaskTypes$ ??= this.taskTypes().pipe(
      map((list) => list.filter((t) => t.canBeSelected && t.enabled).sort((a, b) => a.path.localeCompare(b.path))),
      shareReplay(1),
    );
    return this.selectableTaskTypes$;
  }

  /** Proyectos SDA vigentes que contienen el texto en su código o descripción (máx. 50). */
  searchSdaProjects(q: string, evolutionary: boolean | null): Observable<SdaProject[]> {
    return this.api.sdaProjects(q.trim(), evolutionary);
  }

  /**
   * Issues de Jira vigentes que contienen el texto (máx. 50), dentro del SDA
   * indicado o, si la actividad no lleva SDA, solo las que no tienen proyecto.
   */
  searchJiraIssues(q: string, sdaProjectId: string | null, withoutSda: boolean): Observable<JiraIssue[]> {
    return this.api.jiraIssues(q.trim(), sdaProjectId, withoutSda);
  }

  nonSdaProjects(): Observable<NonSdaProject[]> {
    this.nonSdaProjects$ ??= this.api.nonSdaProjects().pipe(
      map((list) => list.filter((p) => !p.disabledDate).sort((a, b) => a.description.localeCompare(b.description))),
      shareReplay(1),
    );
    return this.nonSdaProjects$;
  }

  invalidate(): void {
    this.taskTypes$ = undefined;
    this.selectableTaskTypes$ = undefined;
    this.nonSdaProjects$ = undefined;
  }
}
