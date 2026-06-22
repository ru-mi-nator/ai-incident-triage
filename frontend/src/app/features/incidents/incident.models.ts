import { AuthenticatedUser } from '../../core/auth/auth.models';

export type ApplicationName =
  | 'AUTH_SERVICE'
  | 'PAYMENT_SERVICE'
  | 'ORDER_SERVICE'
  | 'NOTIFICATION_SERVICE'
  | 'REPORTING_SERVICE';

export type IncidentEnvironment = 'DEV' | 'QA' | 'UAT' | 'PROD';
export type IncidentStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';
export type IncidentPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IncidentCategory =
  | 'API'
  | 'DATABASE'
  | 'AUTHENTICATION'
  | 'DEPLOYMENT'
  | 'PERFORMANCE'
  | 'NETWORK'
  | 'INTEGRATION'
  | 'CONFIGURATION'
  | 'OTHER';

export interface IncidentSummary {
  readonly id: number;
  readonly displayId: string;
  readonly title: string;
  readonly applicationName: ApplicationName;
  readonly environment: IncidentEnvironment;
  readonly status: IncidentStatus;
  readonly priority: IncidentPriority | null;
  readonly assignedDeveloper: AuthenticatedUser | null;
  readonly createdAt: string;
}

export interface IncidentPage {
  readonly content: readonly IncidentSummary[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

export interface CreateIncidentRequest {
  readonly title: string;
  readonly description: string;
  readonly applicationName: ApplicationName;
  readonly environment: IncidentEnvironment;
  readonly errorLogs: string | null;
}

export interface CreatedIncident {
  readonly id: number;
  readonly displayId: string;
  readonly status: IncidentStatus;
  readonly createdBy: AuthenticatedUser;
  readonly assignedDeveloper: AuthenticatedUser | null;
}

export interface IncidentUserSummary {
  readonly id: number;
  readonly name: string;
  readonly username: string;
  readonly role: AuthenticatedUser['role'];
}

export interface IncidentAiAnalysis {
  readonly suggestedCategory: IncidentCategory;
  readonly suggestedPriority: IncidentPriority;
  readonly probableRootCause: string;
  readonly suggestedResolution: string;
  readonly modelName: string;
  readonly generatedAt: string;
}

export interface IncidentDetails {
  readonly id: number;
  readonly displayId: string;
  readonly title: string;
  readonly description: string;
  readonly applicationName: ApplicationName;
  readonly environment: IncidentEnvironment;
  readonly errorLogs: string | null;
  readonly status: IncidentStatus;
  readonly createdBy: IncidentUserSummary;
  readonly assignedDeveloper: IncidentUserSummary | null;
  readonly assignedAt: string | null;
  readonly finalCategory: IncidentCategory | null;
  readonly finalPriority: IncidentPriority | null;
  readonly actualRootCause: string | null;
  readonly actualResolution: string | null;
  readonly resolvedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly aiAnalysis: IncidentAiAnalysis | null;
}
