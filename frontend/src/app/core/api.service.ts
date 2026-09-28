import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CatalogStatus, CatalogSyncResult, Dashboard, Day, DayUpsert, FeedbackFile, FeedbackRow, FeedbackSyncResult,
  Holiday, HolidayCreate, ImportResult, JiraIssue, Me, MonthView, NonSdaProject, Notification, NotificationConfig,
  NotificationRunResult, Person, PersonInput, RegistrationFeedbackResult, RegistrationOverview, RegistrationUploadResult,
  SdaProject, TaskType, Template, TemplateCreate, UploadPreview, UploadQuota, UploadRecord, UploadRunResult, User,
  UserCreate, UserUpdate,
} from './models';

/** Cliente tipado de la API del panel (docs/panel-api.md). Todas las rutas son relativas a /api. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  private params(obj: Record<string, string | number | boolean | null | undefined>): HttpParams {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(obj)) {
      if (v !== null && v !== undefined && v !== '') p = p.set(k, String(v));
    }
    return p;
  }

  // ---- Auth ----
  login(email: string, password: string): Observable<Me> {
    return this.http.post<Me>(`${this.base}/auth/login`, { email, password });
  }
  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, {});
  }
  me(): Observable<Me> {
    return this.http.get<Me>(`${this.base}/auth/me`);
  }

  // ---- Usuarios ----
  users(): Observable<User[]> {
    return this.http.get<User[]>(`${this.base}/users`);
  }
  createUser(body: UserCreate): Observable<User> {
    return this.http.post<User>(`${this.base}/users`, body);
  }
  updateUser(id: number, body: UserUpdate): Observable<User> {
    return this.http.put<User>(`${this.base}/users/${id}`, body);
  }

  // ---- Personas ----
  persons(active?: boolean): Observable<Person[]> {
    return this.http.get<Person[]>(`${this.base}/persons`, { params: this.params({ active }) });
  }
  person(id: number): Observable<Person> {
    return this.http.get<Person>(`${this.base}/persons/${id}`);
  }
  createPerson(body: PersonInput): Observable<Person> {
    return this.http.post<Person>(`${this.base}/persons`, body);
  }
  updatePerson(id: number, body: PersonInput): Observable<Person> {
    return this.http.put<Person>(`${this.base}/persons/${id}`, body);
  }
  deactivatePerson(id: number): Observable<Person> {
    return this.http.delete<Person>(`${this.base}/persons/${id}`);
  }
  templates(personId: number): Observable<Template[]> {
    return this.http.get<Template[]>(`${this.base}/persons/${personId}/templates`);
  }
  createTemplate(personId: number, body: TemplateCreate): Observable<Template> {
    return this.http.post<Template>(`${this.base}/persons/${personId}/templates`, body);
  }
  deleteTemplate(personId: number, templateId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/persons/${personId}/templates/${templateId}`);
  }
  days(personId: number, from: string, to: string): Observable<Day[]> {
    return this.http.get<Day[]>(`${this.base}/persons/${personId}/days`, { params: this.params({ from, to }) });
  }
  upsertDay(personId: number, date: string, body: DayUpsert): Observable<Day> {
    return this.http.put<Day>(`${this.base}/persons/${personId}/days/${date}`, body);
  }
  deleteDay(personId: number, date: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/persons/${personId}/days/${date}`);
  }
  month(personId: number, year: number, month: number): Observable<MonthView> {
    return this.http.get<MonthView>(`${this.base}/persons/${personId}/month`, { params: this.params({ year, month }) });
  }

  // ---- Catálogos ----
  taskTypes(): Observable<TaskType[]> {
    return this.http.get<TaskType[]>(`${this.base}/catalogs/task-types`);
  }
  /** Búsqueda en servidor: el catálogo real tiene más de 11.000 proyectos. */
  sdaProjects(q: string, evolutionary: boolean | null, limit = 50): Observable<SdaProject[]> {
    return this.http.get<SdaProject[]>(`${this.base}/catalogs/sda-projects`, {
      params: this.params({ q, evolutionary: evolutionary === null ? '' : String(evolutionary), limit }),
    });
  }
  /** Búsqueda en servidor: el catálogo real tiene más de 260.000 issues. */
  jiraIssues(q: string, sdaProjectId: string | null, withoutSda: boolean, limit = 50): Observable<JiraIssue[]> {
    return this.http.get<JiraIssue[]>(`${this.base}/catalogs/jira-issues`, {
      params: this.params({ q, sdaProjectId: sdaProjectId ?? '', withoutSda, limit }),
    });
  }
  nonSdaProjects(): Observable<NonSdaProject[]> {
    return this.http.get<NonSdaProject[]>(`${this.base}/catalogs/non-sda-projects`);
  }
  catalogStatus(): Observable<CatalogStatus[]> {
    return this.http.get<CatalogStatus[]>(`${this.base}/catalogs`);
  }
  syncCatalogs(): Observable<CatalogSyncResult[]> {
    return this.http.post<CatalogSyncResult[]>(`${this.base}/catalogs/sync`, {});
  }

  // ---- Envíos ----
  uploadPreview(date?: string | null): Observable<UploadPreview> {
    return this.http.get<UploadPreview>(`${this.base}/uploads/preview`, { params: this.params({ date }) });
  }
  uploadRun(): Observable<UploadRunResult> {
    return this.http.post<UploadRunResult>(`${this.base}/uploads/run`, {});
  }
  uploadQuota(): Observable<UploadQuota> {
    return this.http.get<UploadQuota>(`${this.base}/uploads/quota`);
  }
  uploads(): Observable<UploadRecord[]> {
    return this.http.get<UploadRecord[]>(`${this.base}/uploads`);
  }
  uploadFileUrl(id: number): string {
    return `${this.base}/uploads/${id}/file`;
  }

  // ---- Feedback ----
  feedbackFiles(): Observable<FeedbackFile[]> {
    return this.http.get<FeedbackFile[]>(`${this.base}/feedback`);
  }
  feedbackPending(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/feedback/pending`);
  }
  feedbackSync(): Observable<FeedbackSyncResult[]> {
    return this.http.post<FeedbackSyncResult[]>(`${this.base}/feedback/sync`, {});
  }
  feedbackRows(date: string, onlyRejected = true): Observable<FeedbackRow[]> {
    return this.http.get<FeedbackRow[]>(`${this.base}/feedback/${date}/rows`, { params: this.params({ onlyRejected }) });
  }

  // ---- Festivos ----
  holidays(geography?: string | null, year?: number | null): Observable<Holiday[]> {
    return this.http.get<Holiday[]>(`${this.base}/holidays`, { params: this.params({ geography, year }) });
  }
  createHoliday(body: HolidayCreate): Observable<Holiday> {
    return this.http.post<Holiday>(`${this.base}/holidays`, body);
  }
  deleteHoliday(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/holidays/${id}`);
  }

  // ---- Situación del periodo ----
  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${this.base}/dashboard`);
  }

  // ---- Avisos ----
  notifications(limit = 100): Observable<Notification[]> {
    return this.http.get<Notification[]>(`${this.base}/notifications`, { params: this.params({ limit }) });
  }
  runNotifications(): Observable<NotificationRunResult> {
    return this.http.post<NotificationRunResult>(`${this.base}/notifications/run`, {});
  }
  notificationsConfig(): Observable<NotificationConfig> {
    return this.http.get<NotificationConfig>(`${this.base}/notifications/config`);
  }

  // ---- Registro de usuarios en Activity Report ----
  registrations(): Observable<RegistrationOverview> {
    return this.http.get<RegistrationOverview>(`${this.base}/registrations`);
  }
  runRegistrations(): Observable<RegistrationUploadResult> {
    return this.http.post<RegistrationUploadResult>(`${this.base}/registrations/run`, {});
  }
  syncRegistrationFeedback(): Observable<RegistrationFeedbackResult[]> {
    return this.http.post<RegistrationFeedbackResult[]>(`${this.base}/registrations/feedback/sync`, {});
  }
  requestRegistration(personId: number): Observable<Person> {
    return this.http.post<Person>(`${this.base}/registrations/persons/${personId}/request`, {});
  }
  cancelRegistration(personId: number): Observable<Person> {
    return this.http.post<Person>(`${this.base}/registrations/persons/${personId}/cancel`, {});
  }

  // ---- Importación CSV ----
  importDays(file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<ImportResult>(`${this.base}/imports/days`, form);
  }
}
