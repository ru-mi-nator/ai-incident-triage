import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import { catchError, map, merge, of, Subject, switchMap, tap } from 'rxjs';
import { ApiErrorResponse } from '../../core/auth/auth.models';
import { roleLabel } from '../../core/auth/role-label';
import { enumLabel } from './incident-labels';
import { IncidentDetails } from './incident.models';
import { IncidentService } from './incident.service';

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 10;
const PAGE_SIZES = [5, 10, 20, 50] as const;

type DetailsState =
  | { readonly kind: 'loaded'; readonly incident: IncidentDetails }
  | { readonly kind: 'invalid' | 'not-found' | 'forbidden' | 'error' };

@Component({
  selector: 'app-incident-details',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './incident-details.component.html',
  styleUrl: './incident-details.component.scss'
})
export class IncidentDetailsComponent {
  private readonly incidentService = inject(IncidentService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly retries = new Subject<void>();

  readonly incident = signal<IncidentDetails | null>(null);
  readonly loading = signal(true);
  readonly state = signal<DetailsState['kind']>('invalid');
  readonly backQueryParams = signal({ page: DEFAULT_PAGE, size: DEFAULT_SIZE });
  readonly label = enumLabel;
  readonly role = roleLabel;

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
          return merge(of(undefined), this.retries).pipe(
            tap(() => {
              this.incident.set(null);
              this.loading.set(id !== null);
              this.state.set(id === null ? 'invalid' : 'error');
            }),
            switchMap(() => {
              if (id === null) {
                return of({ kind: 'invalid' } satisfies DetailsState);
              }
              return this.incidentService.getIncidentById(id).pipe(
                map(incident => ({ kind: 'loaded', incident }) satisfies DetailsState),
                catchError((error: unknown) => of(this.errorState(error)))
              );
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(result => {
        this.state.set(result.kind);
        this.incident.set(result.kind === 'loaded' ? result.incident : null);
        this.loading.set(false);
      });
  }

  retry(): void {
    if (!this.loading()) {
      this.retries.next();
    }
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
