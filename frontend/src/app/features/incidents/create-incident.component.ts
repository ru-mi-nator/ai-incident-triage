import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { finalize, Observable } from 'rxjs';
import { ApiErrorResponse } from '../../core/auth/auth.models';
import { enumLabel } from './incident-labels';
import {
  ApplicationName,
  CreateIncidentRequest,
  IncidentEnvironment
} from './incident.models';
import { IncidentService } from './incident.service';

const APPLICATIONS: readonly ApplicationName[] = [
  'AUTH_SERVICE',
  'PAYMENT_SERVICE',
  'ORDER_SERVICE',
  'NOTIFICATION_SERVICE',
  'REPORTING_SERVICE'
];
const ENVIRONMENTS: readonly IncidentEnvironment[] = ['DEV', 'QA', 'UAT', 'PROD'];
const RECOGNIZED_FIELDS = ['title', 'description', 'applicationName', 'environment', 'errorLogs'] as const;
type CreateFieldName = typeof RECOGNIZED_FIELDS[number];

function nonBlankValidator(): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null =>
    control.value.trim().length > 0 ? null : { blank: true };
}

function trimmedMaxLengthValidator(maxLength: number): ValidatorFn {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const actualLength = control.value.trim().length;
    return actualLength <= maxLength
      ? null
      : { maxlength: { requiredLength: maxLength, actualLength } };
  };
}

@Component({
  selector: 'app-create-incident',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule
  ],
  templateUrl: './create-incident.component.html',
  styleUrl: './create-incident.component.scss'
})
export class CreateIncidentComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly incidentService = inject(IncidentService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly applications = APPLICATIONS;
  readonly environments = ENVIRONMENTS;
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly applicationLabel = enumLabel;
  readonly createForm = this.formBuilder.nonNullable.group({
    title: ['', [Validators.required, nonBlankValidator(), trimmedMaxLengthValidator(150)]],
    description: ['', [Validators.required, nonBlankValidator(), trimmedMaxLengthValidator(2000)]],
    applicationName: this.formBuilder.control<ApplicationName | null>(null, Validators.required),
    environment: this.formBuilder.control<IncidentEnvironment | null>(null, Validators.required),
    errorLogs: ['', trimmedMaxLengthValidator(10000)]
  });

  constructor() {
    for (const field of RECOGNIZED_FIELDS) {
      const control = this.createForm.controls[field];
      (control.valueChanges as Observable<unknown>)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.clearServerError(field));
    }
  }

  characterCount(value: string): number {
    return value.trim().length;
  }

  environmentLabel(environment: IncidentEnvironment): string {
    const labels: Record<IncidentEnvironment, string> = {
      DEV: 'Development',
      QA: 'QA',
      UAT: 'UAT',
      PROD: 'Production'
    };
    return labels[environment];
  }

  submit(): void {
    if (this.createForm.invalid || this.submitting()) {
      this.createForm.markAllAsTouched();
      return;
    }

    const raw = this.createForm.getRawValue();
    if (!raw.applicationName || !raw.environment) {
      return;
    }
    const trimmedLogs = raw.errorLogs.trim();
    const request: CreateIncidentRequest = {
      title: raw.title.trim(),
      description: raw.description.trim(),
      applicationName: raw.applicationName,
      environment: raw.environment,
      errorLogs: trimmedLogs.length > 0 ? trimmedLogs : null
    };

    this.errorMessage.set(null);
    this.submitting.set(true);
    this.incidentService.createIncident(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.submitting.set(false))
      )
      .subscribe({
        next: incident => {
          this.snackBar.open(
            `Incident ${incident.displayId} created successfully`,
            'Dismiss',
            { duration: 5000 }
          );
          void this.router.navigate(['/incidents'], { queryParams: { page: 0, size: 10 } });
        },
        error: error => this.handleError(error)
      });
  }

  private handleError(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.errorMessage.set('Incident could not be created. Please try again.');
      return;
    }
    if (error.status === 401) {
      return;
    }
    if (error.status === 403) {
      this.errorMessage.set('You do not have permission to create incidents.');
      return;
    }

    const apiError = error.error as Partial<ApiErrorResponse> | null;
    if (error.status === 400 && apiError?.errorCode === 'VALIDATION_FAILED') {
      this.mapFieldErrors(apiError.fieldErrors);
      this.errorMessage.set('Please review the highlighted fields.');
      return;
    }
    this.errorMessage.set('Incident could not be created. Please try again.');
  }

  private mapFieldErrors(fieldErrors: Readonly<Record<string, string>> | undefined): void {
    if (!fieldErrors) {
      return;
    }
    for (const field of RECOGNIZED_FIELDS) {
      if (fieldErrors[field]) {
        const control = this.createForm.controls[field as CreateFieldName];
        control.setErrors({ ...control.errors, server: true });
        control.markAsTouched();
      }
    }
  }

  private clearServerError(field: CreateFieldName): void {
    const control = this.createForm.controls[field];
    if (control.hasError('server')) {
      const { server: _server, ...remaining } = control.errors ?? {};
      control.setErrors(Object.keys(remaining).length > 0 ? remaining : null);
    }
  }
}
