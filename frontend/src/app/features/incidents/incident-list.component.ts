import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, map, of, Subject, switchMap, tap } from 'rxjs';
import { enumLabel } from './incident-labels';
import { IncidentPage, IncidentSummary } from './incident.models';
import { IncidentService } from './incident.service';

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 10;
const DEFAULT_SORT = 'createdAt,desc';
const PAGE_SIZES = [5, 10, 20, 50] as const;

interface IncidentPageRequest {
  readonly page: number;
  readonly size: number;
}

type IncidentPageResult =
  | { readonly success: true; readonly response: IncidentPage }
  | { readonly success: false; readonly error: unknown };

@Component({
  selector: 'app-incident-list',
  imports: [
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTableModule
  ],
  templateUrl: './incident-list.component.html',
  styleUrl: './incident-list.component.scss'
})
export class IncidentListComponent {
  private readonly incidentService = inject(IncidentService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pageRequests = new Subject<IncidentPageRequest>();

  readonly incidents = signal<readonly IncidentSummary[]>([]);
  readonly pageIndex = signal(DEFAULT_PAGE);
  readonly pageSize = signal(DEFAULT_SIZE);
  readonly totalElements = signal(0);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly pageSizeOptions = PAGE_SIZES;
  readonly displayedColumns = [
    'incident',
    'application',
    'environment',
    'status',
    'priority',
    'assignee',
    'createdAt'
  ];
  readonly label = enumLabel;

  constructor() {
    this.pageRequests
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.errorMessage.set(null);
        }),
        switchMap(({ page, size }) =>
          this.incidentService.getIncidents(page, size, DEFAULT_SORT).pipe(
            map(response => ({ success: true, response }) satisfies IncidentPageResult),
            catchError((error: unknown) =>
              of({ success: false, error } satisfies IncidentPageResult)
            )
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(result => this.applyResult(result));

    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => {
        const page = this.validPage(params.get('page'));
        const size = this.validSize(params.get('size'));
        this.pageIndex.set(page);
        this.pageSize.set(size);
        this.requestPage(page, size);
      });
  }

  onPage(event: PageEvent): void {
    if (this.loading()) {
      return;
    }
    const page = event.pageSize === this.pageSize() ? event.pageIndex : DEFAULT_PAGE;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page, size: event.pageSize },
      queryParamsHandling: 'merge'
    });
  }

  refresh(): void {
    if (!this.loading()) {
      this.requestPage(this.pageIndex(), this.pageSize());
    }
  }

  retry(): void {
    this.requestPage(this.pageIndex(), this.pageSize());
  }

  safeDate(value: string | null): Date | null {
    if (!value) {
      return null;
    }
    const timestamp = Date.parse(value);
    return Number.isNaN(timestamp) ? null : new Date(timestamp);
  }

  private requestPage(page: number, size: number): void {
    this.pageRequests.next({ page, size });
  }

  private applyResult(result: IncidentPageResult): void {
    if (!result.success) {
      this.incidents.set([]);
      this.totalElements.set(0);
      this.errorMessage.set(this.safeErrorMessage(result.error));
      this.loading.set(false);
      return;
    }

    const response = result.response;
    if (
      response.content.length === 0
      && response.totalElements > 0
      && response.page >= response.totalPages
    ) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { page: response.totalPages - 1, size: response.size },
        queryParamsHandling: 'merge',
        replaceUrl: true
      });
      return;
    }

    this.applyPage(response);
    this.loading.set(false);
  }

  private applyPage(response: IncidentPage): void {
    this.incidents.set(response.content);
    this.pageIndex.set(response.page);
    this.pageSize.set(response.size);
    this.totalElements.set(response.totalElements);
  }

  private safeErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'You do not have access to view incidents.';
    }
    return 'Incidents could not be loaded. Please try again.';
  }

  private validPage(value: string | null): number {
    if (value === null || !/^\d+$/.test(value)) {
      return DEFAULT_PAGE;
    }
    const parsed = Number(value);
    return Number.isSafeInteger(parsed) ? parsed : DEFAULT_PAGE;
  }

  private validSize(value: string | null): number {
    const parsed = Number(value);
    return PAGE_SIZES.includes(parsed as typeof PAGE_SIZES[number]) ? parsed : DEFAULT_SIZE;
  }
}
