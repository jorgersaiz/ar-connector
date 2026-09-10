import { Injectable, inject } from '@angular/core';
import { Observable, map, shareReplay } from 'rxjs';
import { ApiService } from './api.service';
import { JiraIssue, NonSdaProject, SdaProject, TaskType } from './models';

/** Caché de catálogos durante la sesión (se invalida tras "Sincronizar ahora"). */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly api = inject(ApiService);

  private taskTypes$?: Observable<TaskType[]>;
  private selectableTaskTypes$?: Observable<TaskType[]>;
  private sdaProjects$?: Observable<SdaProject[]>;
  private nonSdaProjects$?: Observable<NonSdaProject[]>;
  private readonly jiraIssues = new Map<string, Observable<JiraIssue[]>>();

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

  /** Proyectos SDA activos (sin disabledDate). */
  sdaProjects(): Observable<SdaProject[]> {
    this.sdaProjects$ ??= this.api.sdaProjects().pipe(
      map((list) => list.filter((p) => !p.disabledDate).sort((a, b) => a.sdaProjectId.localeCompare(b.sdaProjectId))),
      shareReplay(1),
    );
    return this.sdaProjects$;
  }

  jiraIssuesOf(sdaProjectId: string): Observable<JiraIssue[]> {
    let obs = this.jiraIssues.get(sdaProjectId);
    if (!obs) {
      obs = this.api.jiraIssues(sdaProjectId).pipe(shareReplay(1));
      this.jiraIssues.set(sdaProjectId, obs);
    }
    return obs;
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
    this.sdaProjects$ = undefined;
    this.nonSdaProjects$ = undefined;
    this.jiraIssues.clear();
  }
}
