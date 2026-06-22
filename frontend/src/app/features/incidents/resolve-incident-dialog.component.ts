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
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize, Observable } from 'rxjs';
import { ApiErrorResponse } from '../../core/auth/auth.models';
import { enumLabel } from './incident-labels';
import {
  IncidentCategory,
  IncidentDetails,
  IncidentPriority,
  ResolveIncidentRequest
} from './incident.models';
import { IncidentService } from './incident.service';

export interface ResolveIncidentDialogData {
  readonly incidentId: number;
}

const CATEGORIES: readonly IncidentCategory[] = [
  'API',
  'DATABASE',
  'AUTHENTICATION',
  'DEPLOYMENT',
  'PERFORMANCE',
  'NETWORK',
  'INTEGRATION',
  'CONFIGURATION',
  'OTHER'
];
const PRIORITIES: readonly IncidentPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const RESOLUTION_FIELDS = [
  'finalCategory',
  'finalPriority',
  'actualRootCause',
  'actualResolution'
] as const;
type ResolutionField = typeof RESOLUTION_FIELDS[number];

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
  selector: 'app-resolve-incident-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule
  ],
  templateUrl: './resolve-incident-dialog.component.html',
  styleUrl: './resolve-incident-dialog.component.scss'
})
export class ResolveIncidentDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly incidentService = inject(IncidentService);
  private readonly dialogRef = inject(MatDialogRef<ResolveIncidentDialogComponent, IncidentDetails>);
  private readonly data = inject<ResolveIncidentDialogData>(MAT_DIALOG_DATA);
  private readonly destroyRef = inject(DestroyRef);

  readonly categories = CATEGORIES;
  readonly priorities = PRIORITIES;
  readonly label = enumLabel;
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly resolveForm = this.formBuilder.nonNullable.group({
    finalCategory: this.formBuilder.control<IncidentCategory | null>(null, Validators.required),
    finalPriority: this.formBuilder.control<IncidentPriority | null>(null, Validators.required),
    actualRootCause: [
      '',
      [Validators.required, nonBlankValidator(), trimmedMaxLengthValidator(2000)]
    ],
    actualResolution: [
      '',
      [Validators.required, nonBlankValidator(), trimmedMaxLengthValidator(3000)]
    ]
  });

  constructor() {
    for (const field of RESOLUTION_FIELDS) {
      (this.resolveForm.controls[field].valueChanges as Observable<unknown>)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.clearServerError(field));
    }
  }

  characterCount(value: string): number {
    return value.trim().length;
  }

  submit(): void {
    if (this.resolveForm.invalid || this.submitting()) {
      this.resolveForm.markAllAsTouched();
      return;
    }

    const raw = this.resolveForm.getRawValue();
    if (!raw.finalCategory || !raw.finalPriority) {
      return;
    }
    const request: ResolveIncidentRequest = {
      finalCategory: raw.finalCategory,
      finalPriority: raw.finalPriority,
      actualRootCause: raw.actualRootCause.trim(),
      actualResolution: raw.actualResolution.trim()
    };

    this.errorMessage.set(null);
    this.submitting.set(true);
    this.dialogRef.disableClose = true;
    this.incidentService.resolveIncident(this.data.incidentId, request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.submitting.set(false);
          this.dialogRef.disableClose = false;
        })
      )
      .subscribe({
        next: incident => this.dialogRef.close(incident),
        error: error => this.handleError(error)
      });
  }

  private handleError(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.errorMessage.set('Incident could not be resolved. Please try again.');
      return;
    }
    if (error.status === 401) {
      return;
    }

    const apiError = error.error as Partial<ApiErrorResponse> | null;
    const fallbackCode = error.status === 403
      ? 'ACCESS_DENIED'
      : error.status === 409
        ? 'INCIDENT_NOT_RESOLVABLE'
        : undefined;
    switch (apiError?.errorCode ?? fallbackCode) {
      case 'INCIDENT_NOT_RESOLVABLE':
        this.errorMessage.set('This incident cannot be resolved in its current state.');
        return;
      case 'ACCESS_DENIED':
        this.errorMessage.set('You do not have permission to resolve this incident.');
        return;
      case 'VALIDATION_FAILED':
        this.mapFieldErrors(apiError?.fieldErrors);
        this.errorMessage.set('Please review the highlighted fields.');
        return;
      case 'INVALID_REQUEST_BODY':
        this.errorMessage.set('Please review the highlighted fields.');
        return;
      default:
        this.errorMessage.set('Incident could not be resolved. Please try again.');
    }
  }

  private mapFieldErrors(fieldErrors: Readonly<Record<string, string>> | undefined): void {
    if (!fieldErrors) {
      return;
    }
    for (const field of RESOLUTION_FIELDS) {
      if (fieldErrors[field]) {
        const control = this.resolveForm.controls[field];
        control.setErrors({ ...control.errors, server: true });
        control.markAsTouched();
      }
    }
  }

  private clearServerError(field: ResolutionField): void {
    const control = this.resolveForm.controls[field];
    if (control.hasError('server')) {
      const { server: _server, ...remaining } = control.errors ?? {};
      control.setErrors(Object.keys(remaining).length > 0 ? remaining : null);
    }
  }
}
