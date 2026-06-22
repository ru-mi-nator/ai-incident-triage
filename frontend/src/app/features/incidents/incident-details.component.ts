import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import {
  catchError,
  finalize,
  map,
  merge,
  of,
  Subject,
  switchMap,
  takeUntil,
  tap
} from 'rxjs';
import { ApiErrorResponse } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { roleLabel } from '../../core/auth/role-label';
import { enumLabel } from './incident-labels';
import { IncidentDetails } from './incident.models';
import { IncidentService } from './incident.service';
import {
  ResolveIncidentDialogComponent,
  ResolveIncidentDialogData
} from './resolve-incident-dialog.component';

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 10;
const PAGE_SIZES = [5, 10, 20, 50] as const;

type DetailsState =
  | { readonly kind: 'loaded'; readonly incident: IncidentDetails }
  | { readonly kind: 'invalid' | 'not-found' | 'forbidden' | 'error' };
type ActionKind = 'assign' | 'analyze' | 'resolve';

interface DetailsResult {
  readonly state: DetailsState;
  readonly routeGeneration: number;
  readonly incidentId: number | null;
  readonly mutationGeneration: number;
}

@Component({
  selector: 'app-incident-details',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './incident-details.component.html',
  styleUrl: './incident-details.component.scss'
})
export class IncidentDetailsComponent {
  private readonly incidentService = inject(IncidentService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retries = new Subject<void>();
  private readonly cancelActions = new Subject<void>();
  private routeGeneration = 0;
  private mutationGeneration = 0;
  private currentIncidentId: number | null = null;
  private resolveDialogRef: MatDialogRef<
    ResolveIncidentDialogComponent,
    IncidentDetails
  > | null = null;

  readonly incident = signal<IncidentDetails | null>(null);
  readonly loading = signal(true);
  readonly state = signal<DetailsState['kind']>('invalid');
  readonly activeAction = signal<ActionKind | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly backQueryParams = signal({ page: DEFAULT_PAGE, size: DEFAULT_SIZE });
  readonly label = enumLabel;
  readonly role = roleLabel;
  readonly assigning = computed(() => this.activeAction() === 'assign');
  readonly analyzing = computed(() => this.activeAction() === 'analyze');
  readonly resolving = computed(() => this.activeAction() === 'resolve');

  constructor() {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => this.backQueryParams.set({
        page: this.validPage(params.get('page')),
        size: this.validSize(params.get('size'))
      }));

    this.route.paramMap
      .pipe(
        switchMap(params => {
          const id = this.validId(params);
          this.beginRoute(id);
          const routeGeneration = this.routeGeneration;

          return merge(of(undefined), this.retries).pipe(
            tap(() => {
              this.incident.set(null);
              this.loading.set(id !== null);
              this.state.set(id === null ? 'invalid' : 'error');
              this.actionError.set(null);
            }),
            switchMap(() => {
              const mutationGeneration = this.mutationGeneration;
              if (id === null) {
                return of({
                  state: { kind: 'invalid' },
                  routeGeneration,
                  incidentId: null,
                  mutationGeneration
                } satisfies DetailsResult);
              }
              return this.incidentService.getIncidentById(id).pipe(
                map(incident => ({
                  state: { kind: 'loaded', incident },
                  routeGeneration,
                  incidentId: id,
                  mutationGeneration
                }) satisfies DetailsResult),
                catchError((error: unknown) => of({
                  state: this.errorState(error),
                  routeGeneration,
                  incidentId: id,
                  mutationGeneration
                } satisfies DetailsResult))
              );
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(result => {
        if (!this.isCurrentContext(result.incidentId, result.routeGeneration)
          || result.mutationGeneration !== this.mutationGeneration) {
          return;
        }
        this.state.set(result.state.kind);
        this.incident.set(result.state.kind === 'loaded' ? result.state.incident : null);
        this.loading.set(false);
      });
  }

  retry(): void {
    if (!this.loading()) {
      this.retries.next();
    }
  }

  canAssign(incident: IncidentDetails): boolean {
    const user = this.authService.currentUser();
    return user?.role === 'DEVELOPER'
      && incident.status === 'OPEN'
      && incident.assignedDeveloper === null;
  }

  canAnalyze(incident: IncidentDetails): boolean {
    const user = this.authService.currentUser();
    if (!user || incident.aiAnalysis !== null || incident.status === 'RESOLVED') {
      return false;
    }
    if (user.role === 'SUPPORT_ENGINEER') {
      return incident.status === 'OPEN' && incident.createdBy.id === user.id;
    }
    return incident.status === 'IN_PROGRESS'
      && incident.assignedDeveloper?.id === user.id;
  }

  canResolve(incident: IncidentDetails): boolean {
    const user = this.authService.currentUser();
    return user?.role === 'DEVELOPER'
      && incident.status === 'IN_PROGRESS'
      && incident.assignedDeveloper?.id === user.id;
  }

  assignToMe(): void {
    const context = this.actionContext('assign', incident => this.canAssign(incident));
    if (!context) {
      return;
    }

    this.incidentService.assignToMe(context.incident.id)
      .pipe(
        takeUntil(this.cancelActions),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.finishAction('assign', context.routeGeneration))
      )
      .subscribe({
        next: incident => {
          if (!this.applyActionResult(incident, context)) {
            return;
          }
          this.snackBar.open('Incident assigned to you.', 'Dismiss', { duration: 5000 });
        },
        error: error => this.handleActionError('assign', error, context)
      });
  }

  analyzeIncident(): void {
    const context = this.actionContext('analyze', incident => this.canAnalyze(incident));
    if (!context) {
      return;
    }

    this.incidentService.analyzeIncident(context.incident.id)
      .pipe(
        takeUntil(this.cancelActions),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.finishAction('analyze', context.routeGeneration))
      )
      .subscribe({
        next: incident => {
          if (!this.applyActionResult(incident, context)) {
            return;
          }
          this.snackBar.open('AI analysis generated.', 'Dismiss', { duration: 5000 });
        },
        error: error => this.handleActionError('analyze', error, context)
      });
  }

  openResolveDialog(): void {
    const context = this.actionContext('resolve', incident => this.canResolve(incident));
    if (!context) {
      return;
    }

    const data: ResolveIncidentDialogData = { incidentId: context.incident.id };
    this.resolveDialogRef = this.dialog.open<
      ResolveIncidentDialogComponent,
      ResolveIncidentDialogData,
      IncidentDetails
    >(ResolveIncidentDialogComponent, {
      data,
      width: '48rem',
      maxWidth: '96vw',
      autoFocus: 'first-tabbable',
      ariaDescribedBy: 'resolve-dialog-description',
      restoreFocus: true
    });

    this.resolveDialogRef.afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(incident => {
        this.resolveDialogRef = null;
        this.finishAction('resolve', context.routeGeneration);
        if (!incident || !this.applyActionResult(incident, context)) {
          return;
        }
        this.snackBar.open(
          'Incident resolved successfully.',
          'Dismiss',
          { duration: 5000 }
        );
      });
  }

  safeDate(value: string | null): Date | null {
    if (!value) {
      return null;
    }
    const timestamp = Date.parse(value);
    return Number.isNaN(timestamp) ? null : new Date(timestamp);
  }

  hasCompleteFinalResolution(incident: IncidentDetails): boolean {
    return incident.status === 'RESOLVED'
      && incident.finalCategory !== null
      && incident.finalPriority !== null
      && incident.actualRootCause !== null
      && incident.actualResolution !== null
      && incident.resolvedAt !== null;
  }

  private beginRoute(id: number | null): void {
    this.routeGeneration += 1;
    this.currentIncidentId = id;
    this.cancelActions.next();
    this.resolveDialogRef?.close();
    this.resolveDialogRef = null;
    this.activeAction.set(null);
    this.actionError.set(null);
  }

  private actionContext(
    action: ActionKind,
    isEligible: (incident: IncidentDetails) => boolean
  ): {
    readonly incident: IncidentDetails;
    readonly routeGeneration: number;
  } | null {
    const incident = this.incident();
    if (!incident
      || this.activeAction() !== null
      || this.currentIncidentId !== incident.id
      || !isEligible(incident)) {
      return null;
    }
    this.activeAction.set(action);
    this.actionError.set(null);
    return { incident, routeGeneration: this.routeGeneration };
  }

  private applyActionResult(
    incident: IncidentDetails,
    context: { readonly incident: IncidentDetails; readonly routeGeneration: number }
  ): boolean {
    if (!this.isCurrentContext(context.incident.id, context.routeGeneration)
      || incident.id !== context.incident.id) {
      return false;
    }
    this.mutationGeneration += 1;
    this.incident.set(incident);
    this.state.set('loaded');
    this.loading.set(false);
    this.actionError.set(null);
    return true;
  }

  private finishAction(action: ActionKind, routeGeneration: number): void {
    if (this.routeGeneration === routeGeneration && this.activeAction() === action) {
      this.activeAction.set(null);
    }
  }

  private handleActionError(
    action: 'assign' | 'analyze',
    error: unknown,
    context: { readonly incident: IncidentDetails; readonly routeGeneration: number }
  ): void {
    if (!this.isCurrentContext(context.incident.id, context.routeGeneration)) {
      return;
    }
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return;
    }
    const apiError = error instanceof HttpErrorResponse
      ? error.error as Partial<ApiErrorResponse> | null
      : null;
    const fallbackCode = error instanceof HttpErrorResponse
      ? error.status === 403
        ? 'ACCESS_DENIED'
        : action === 'analyze' && error.status === 503
          ? 'AI_SERVICE_UNAVAILABLE'
          : undefined
      : undefined;
    this.actionError.set(this.actionErrorMessage(
      action,
      apiError?.errorCode ?? fallbackCode
    ));
  }

  private actionErrorMessage(
    action: 'assign' | 'analyze',
    errorCode: string | undefined
  ): string {
    const messages: Readonly<Record<string, string>> = action === 'assign'
      ? {
          INCIDENT_ALREADY_ASSIGNED: 'This incident has already been assigned.',
          INCIDENT_NOT_OPEN: 'Only open incidents can be assigned.',
          ACCESS_DENIED: 'You do not have permission to assign this incident.'
        }
      : {
          AI_ANALYSIS_ALREADY_EXISTS: 'AI analysis already exists for this incident.',
          INCIDENT_NOT_ANALYZABLE: 'This incident cannot be analyzed in its current state.',
          AI_SERVICE_UNAVAILABLE:
            'AI analysis is temporarily unavailable. Please try again later.',
          ACCESS_DENIED: 'You do not have permission to analyze this incident.'
        };
    return errorCode && messages[errorCode]
      ? messages[errorCode]
      : action === 'assign'
        ? 'Incident could not be assigned. Please try again.'
        : 'AI analysis could not be generated. Please try again.';
  }

  private isCurrentContext(id: number | null, routeGeneration: number): boolean {
    return this.currentIncidentId === id && this.routeGeneration === routeGeneration;
  }

  private validId(params: ParamMap): number | null {
    const value = params.get('id');
    if (value === null || !/^[1-9]\d*$/.test(value)) {
      return null;
    }
    const id = Number(value);
    return Number.isSafeInteger(id) ? id : null;
  }

  private validPage(value: string | null): number {
    if (value === null || !/^\d+$/.test(value)) {
      return DEFAULT_PAGE;
    }
    const page = Number(value);
    return Number.isSafeInteger(page) ? page : DEFAULT_PAGE;
  }

  private validSize(value: string | null): number {
    const size = Number(value);
    return PAGE_SIZES.includes(size as typeof PAGE_SIZES[number]) ? size : DEFAULT_SIZE;
  }

  private errorState(error: unknown): DetailsState {
    if (error instanceof HttpErrorResponse) {
      const apiError = error.error as Partial<ApiErrorResponse> | null;
      if (error.status === 404 || apiError?.errorCode === 'INCIDENT_NOT_FOUND') {
        return { kind: 'not-found' };
      }
      if (error.status === 403) {
        return { kind: 'forbidden' };
      }
    }
    return { kind: 'error' };
  }
}
