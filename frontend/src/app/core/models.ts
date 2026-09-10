// Interfaces que reflejan docs/panel-api.md. Fechas en ISO yyyy-MM-dd (string).

export type Role = 'MANAGER' | 'CONSULTANT';

export interface Me {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  personId: number | null;
  personDisplayName: string | null;
}

export interface User {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  personId: number | null;
  active: boolean;
}

export interface UserCreate {
  email: string;
  password: string;
  displayName: string;
  role: Role;
  personId?: number | null;
}

export interface UserUpdate {
  email: string;
  displayName: string;
  role: Role;
  personId?: number | null;
  active: boolean;
  password?: string;
}

export interface Person {
  id: number;
  uid: string;
  email: string;
  displayName: string;
  gpsCode: string;
  geography: string;
  startDate: string;
  endDate: string | null;
  active: boolean;
  /** SHA256 del uid en mayúsculas; lo calcula el backend, no se edita */
  personId: string;
  /** Alta del usuario en Activity Report (la gestiona el conector salvo PENDING/NOT_REQUIRED) */
  registrationStatus: RegistrationStatus;
  registrationSentDate: string | null;
  /** Código APV_UR_xx del rechazo, si lo hay */
  registrationReason: string | null;
}

export type RegistrationStatus = 'NOT_REQUIRED' | 'PENDING' | 'SENT' | 'REGISTERED' | 'REJECTED';

export type PersonInput = Omit<Person, 'id' | 'personId' | 'registrationStatus' | 'registrationSentDate' | 'registrationReason'> & {
  /** Solo se puede pedir el alta (PENDING) o desistir (NOT_REQUIRED); el resto lo pone el conector */
  registrationStatus?: 'PENDING' | 'NOT_REQUIRED';
};

export interface Line {
  taskTypeCode: string;
  sdaProjectId?: string | null;
  jiraIssueId?: string | null;
  nonSdaProject?: string | null;
  minutes: number;
  rejectReason?: string | null;
}

export interface Template {
  id: number;
  validFrom: string;
  validTo: string | null;
  lines: Line[];
}

export interface TemplateCreate {
  validFrom: string;
  validTo?: string | null;
  lines: Line[];
}

export type DayKind = 'WORKED' | 'NON_WORKING';
export type DayOrigin = 'TEMPLATE' | 'MANUAL' | 'CSV_IMPORT';
export type DayClassification = 'WORKING' | 'WEEKEND' | 'NATIONAL_HOLIDAY' | 'LOCAL_HOLIDAY';
export type DaySource = 'EXPLICIT' | 'TEMPLATE' | 'HOLIDAY' | 'NONE';

export interface Day {
  id: number;
  date: string;
  kind: DayKind;
  origin: DayOrigin;
  note: string | null;
  lastSentDate: string | null;
  lastFeedbackDate: string | null;
  rejectReason: string | null;
  hasRejections: boolean;
  totalMinutes: number;
  lines: Line[];
}

export interface DayUpsert {
  kind: DayKind;
  note?: string | null;
  lines?: Line[];
}

export interface DayView {
  date: string;
  classification: DayClassification;
  source: DaySource;
  kind: DayKind | null;
  origin: DayOrigin | null;
  note: string | null;
  lines: Line[];
  totalMinutes: number;
  lastSentDate: string | null;
  lastFeedbackDate: string | null;
  hasRejections: boolean;
  rejectReason: string | null;
  gap: boolean;
  locked: boolean;
  lastSubmissionDate: string | null;
  future: boolean;
}

export interface MonthView {
  personId: number;
  year: number;
  month: number;
  days: DayView[];
}

export interface TaskType {
  code: string;
  parentCode: string | null;
  path: string;
  descriptionEs: string;
  canBeSelected: boolean;
  enabled: boolean;
  requiresSdaProject: boolean;
  requiresJiraIssue: boolean;
  requiresNonSdaProject: boolean;
}

export interface SdaProject {
  sdaProjectId: string;
  description: string;
  status: string | null;
  evolutionarySda: boolean | null;
  disabledDate: string | null;
}

export interface JiraIssue {
  jiraIssueId: string;
  description: string;
  sdaProjectId: string;
  status: string | null;
  type: string | null;
}

export interface NonSdaProject {
  description: string;
  disabledDate: string | null;
}

export interface CatalogStatus {
  catalog: string;
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  rowCount: number | null;
  lastError: string | null;
}

export interface CatalogSyncResult {
  catalog: string;
  success: boolean;
  rowCount: number | null;
  error: string | null;
  millis: number;
}

export interface Problem {
  uid: string | null;
  date: string | null;
  code: string;
  message: string;
}

export interface Gap {
  uid: string;
  date: string;
  lastSubmissionDate: string | null;
}

export interface UploadPreview {
  submissionDate: string;
  dayCount: number;
  rowCount: number;
  excludedDays: number;
  problems: Problem[];
  gaps: Gap[];
  csv: string;
}

export type UploadStatus = 'SENT' | 'SKIPPED_QUOTA' | 'UNCHANGED' | 'NOTHING_TO_SEND' | 'FAILED' | 'NOT_BUILT';

export interface UploadRunResult {
  status: UploadStatus;
  submissionDate: string | null;
  apiCallId: number | null;
  dayCount: number;
  rowCount: number;
  excludedDays: number;
  problems: Problem[];
  gaps: Gap[];
  error: string | null;
}

export interface UploadQuota {
  channel: string;
  day: string;
  callsToday: number;
  limit: number;
  lastOk: boolean | null;
}

export interface UploadRecord {
  id: number;
  channel: string;
  calledAt: string;
  submissionDate: string | null;
  ok: boolean;
  httpStatus: number | null;
  errorMessage: string | null;
  fileName: string | null;
  dayCount: number | null;
  rowCount: number | null;
}

export interface FeedbackFile {
  submissionDate: string;
  downloadedAt: string;
  rowCount: number;
  rejectedCount: number;
  matchedCount: number;
}

export type FeedbackSyncStatus = 'APPLIED' | 'NOT_AVAILABLE_YET' | 'FAILED';

export interface FeedbackSyncResult {
  submissionDate: string;
  status: FeedbackSyncStatus;
  rowCount: number | null;
  rejectedCount: number | null;
  matchedRows: number | null;
  unmatchedRows: number | null;
  error: string | null;
}

export interface FeedbackRow {
  lineNumber: number;
  activityDate: string;
  personId: string;
  taskTypeCode: string | null;
  sdaProjectId: string | null;
  jiraIssueId: string | null;
  nonSdaProject: string | null;
  timeMinutes: number | null;
  reason: string | null;
  matched: boolean;
}

export type HolidayScope = 'NATIONAL' | 'LOCAL';

export interface Holiday {
  id: number;
  geography: string;
  date: string;
  description: string;
  scope: HolidayScope;
}

export interface HolidayCreate {
  geography: string;
  date: string;
  description: string;
  scope: HolidayScope;
}

export interface ImportResult {
  daysCreated: number;
  daysReplaced: number;
  lines: number;
  persons: number;
}

export interface ApiError {
  message?: string;
  errors?: string[];
  problems?: Problem[];
}

// ---- Situación del periodo (dashboard) ----

export interface DashboardPeriod {
  /** Ej. "1ª quincena de septiembre 2026" */
  label: string;
  from: string;
  to: string;
  lastSubmissionDate: string;
  daysLeft: number;
}

export interface DashboardQuota {
  callsToday: number;
  limit: number;
  lastOk: boolean | null;
}

export interface DashboardLastUpload {
  calledAt: string;
  submissionDate: string | null;
  ok: boolean;
  dayCount: number | null;
  rowCount: number | null;
  errorMessage: string | null;
}

export interface DashboardTotals {
  persons: number;
  personsWithGaps: number;
  gaps: number;
  personsWithRejections: number;
  rejections: number;
  excludedDays: number;
}

export interface DashboardPerson {
  /** id numérico de la persona (Person.id), para enlazar a /personas/:id/mes */
  personId: number;
  uid: string;
  displayName: string;
  email: string;
  hasTemplate: boolean;
  gapDates: string[];
  nextGapDeadline: string | null;
  rejectedDates: string[];
  excludedDates: string[];
  problemCodes: string[];
}

export interface Dashboard {
  today: string;
  submissionDate: string;
  periods: DashboardPeriod[];
  quota: DashboardQuota;
  lastUpload: DashboardLastUpload | null;
  pendingFeedbackDates: string[];
  totals: DashboardTotals;
  /** Solo personas con algo que resolver, ordenadas por fecha límite más próxima */
  persons: DashboardPerson[];
}

// ---- Avisos ----

export type NotificationChannel = 'LOG' | 'EMAIL';
export type NotificationKind = 'GAPS_REMINDER' | 'REJECTIONS' | 'MANAGER_SUMMARY';

export interface Notification {
  id: number;
  sentAt: string;
  channel: NotificationChannel;
  kind: NotificationKind;
  recipientEmail: string;
  personId: number | null;
  personDisplayName: string | null;
  subject: string;
  body: string;
  deadline: string | null;
}

export interface NotificationRunDetail {
  kind: NotificationKind;
  recipientEmail: string;
  subject: string;
}

export interface NotificationRunResult {
  sent: number;
  skipped: number;
  channel: NotificationChannel;
  details: NotificationRunDetail[];
}

export interface NotificationConfig {
  channel: NotificationChannel;
  daysBeforeCutoff: number;
  mailEnabled: boolean;
}

// ---- Registro de usuarios en Activity Report ----

export interface RegistrationOverview {
  /** Personas con estado distinto de NOT_REQUIRED */
  persons: Person[];
  quota: DashboardQuota;
  lastUpload: UploadRecord | null;
  pendingFeedbackDates: string[];
}

export type RegistrationUploadStatus = 'SENT' | 'SKIPPED_QUOTA' | 'NOTHING_TO_SEND' | 'FAILED';

export interface RegistrationUploadResult {
  status: RegistrationUploadStatus;
  submissionDate: string | null;
  count: number;
  apiCallId: number | null;
  error: string | null;
}

export interface RegistrationFeedbackResult {
  submissionDate: string;
  status: FeedbackSyncStatus;
  rowCount: number | null;
  registered: number | null;
  rejected: number | null;
  unmatched: number | null;
  error: string | null;
}
