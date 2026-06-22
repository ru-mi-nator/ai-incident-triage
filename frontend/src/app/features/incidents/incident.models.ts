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
