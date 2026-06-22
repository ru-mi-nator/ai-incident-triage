import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Subject, throwError } from 'rxjs';
import { IncidentDetails, ResolveIncidentRequest } from './incident.models';
import {
  ResolveIncidentDialogComponent,
  ResolveIncidentDialogData
} from './resolve-incident-dialog.component';
import { IncidentService } from './incident.service';

describe('ResolveIncidentDialogComponent', () => {
  let fixture: ComponentFixture<ResolveIncidentDialogComponent>;
  let component: ResolveIncidentDialogComponent;
  let service: jasmine.SpyObj<IncidentService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ResolveIncidentDialogComponent, IncidentDetails>>;

  const resolvedIncident: IncidentDetails = {
    id: 42,
    displayId: 'INC-0042',
    title: 'Database connections exhausted',
    description: 'Requests are timing out.',
    applicationName: 'PAYMENT_SERVICE',
    environment: 'PROD',
    errorLogs: null,
    status: 'RESOLVED',
    createdBy: {
      id: 1,
      name: 'Support User',
      username: 'support1',
      role: 'SUPPORT_ENGINEER'
    },
    assignedDeveloper: {
      id: 2,
      name: 'Developer One',
      username: 'developer1',
      role: 'DEVELOPER'
    },
    assignedAt: '2026-06-21T10:35:00Z',
    finalCategory: 'DATABASE',
    finalPriority: 'HIGH',
    actualRootCause: 'Connection pool exhaustion.',
    actualResolution: 'Closed leaked connections.',
    resolvedAt: '2026-06-21T11:00:00Z',
    createdAt: '2026-06-21T10:30:00Z',
    updatedAt: '2026-06-21T11:00:00Z',
    aiAnalysis: null
  };

  beforeEach(async () => {
    service = jasmine.createSpyObj<IncidentService>('IncidentService', ['resolveIncident']);
    dialogRef = jasmine.createSpyObj<
      MatDialogRef<ResolveIncidentDialogComponent, IncidentDetails>
    >('MatDialogRef', ['close']);
    const data: ResolveIncidentDialogData = { incidentId: 42 };

    await TestBed.configureTestingModule({
      imports: [ResolveIncidentDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: IncidentService, useValue: service },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ResolveIncidentDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  function fillValidForm(): void {
    component.resolveForm.setValue({
      finalCategory: 'DATABASE',
      finalPriority: 'HIGH',
      actualRootCause: '  Connection pool exhaustion.  ',
      actualResolution: '  Closed leaked connections.  '
    });
  }

  it('enforces required, nonblank, and length validation', () => {
    component.submit();
    expect(component.resolveForm.invalid).toBeTrue();
    expect(service.resolveIncident).not.toHaveBeenCalled();

    component.resolveForm.patchValue({
      finalCategory: 'DATABASE',
      finalPriority: 'HIGH',
      actualRootCause: '   ',
      actualResolution: '   '
    });
    expect(component.resolveForm.controls.actualRootCause.hasError('blank')).toBeTrue();
    expect(component.resolveForm.controls.actualResolution.hasError('blank')).toBeTrue();

    component.resolveForm.patchValue({
      actualRootCause: 'x'.repeat(2001),
      actualResolution: 'x'.repeat(3001)
    });
    expect(component.resolveForm.controls.actualRootCause.hasError('maxlength')).toBeTrue();
    expect(component.resolveForm.controls.actualResolution.hasError('maxlength')).toBeTrue();
  });

  it('submits raw enum values with trimmed text and prevents duplicates', () => {
    const pending = new Subject<IncidentDetails>();
    service.resolveIncident.and.returnValue(pending.asObservable());
    fillValidForm();

    component.submit();
    component.submit();

    const expected: ResolveIncidentRequest = {
      finalCategory: 'DATABASE',
      finalPriority: 'HIGH',
      actualRootCause: 'Connection pool exhaustion.',
      actualResolution: 'Closed leaked connections.'
    };
    expect(service.resolveIncident).toHaveBeenCalledOnceWith(42, expected);
    expect(component.submitting()).toBeTrue();

    pending.next(resolvedIncident);
    pending.complete();
    expect(dialogRef.close).toHaveBeenCalledOnceWith(resolvedIncident);
  });

  it('preserves values and maps safe validation errors after failure', () => {
    service.resolveIncident.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 400,
      error: {
        errorCode: 'VALIDATION_FAILED',
        message: 'raw backend detail',
        fieldErrors: { actualRootCause: 'Rejected' }
      }
    })));
    fillValidForm();

    component.submit();
    fixture.detectChanges();

    expect(component.resolveForm.controls.actualRootCause.value).toBe(
      '  Connection pool exhaustion.  '
    );
    expect(component.resolveForm.controls.actualRootCause.hasError('server')).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Please review the highlighted fields.');
    expect(fixture.nativeElement.textContent).not.toContain('raw backend detail');
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(component.submitting()).toBeFalse();
  });

  it('clears only the edited field server error and ignores unknown fields', () => {
    service.resolveIncident.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 400,
      error: {
        errorCode: 'VALIDATION_FAILED',
        fieldErrors: {
          actualRootCause: 'Rejected',
          actualResolution: 'Rejected',
          unknownField: 'Ignored'
        }
      }
    })));
    fillValidForm();

    component.submit();
    expect(component.resolveForm.controls.actualRootCause.hasError('server')).toBeTrue();
    expect(component.resolveForm.controls.actualResolution.hasError('server')).toBeTrue();

    component.resolveForm.controls.actualRootCause.setValue('Updated cause');

    expect(component.resolveForm.controls.actualRootCause.hasError('server')).toBeFalse();
    expect(component.resolveForm.controls.actualResolution.hasError('server')).toBeTrue();
    expect(Object.keys(component.resolveForm.controls)).not.toContain('unknownField');
  });

  it('shows safe forbidden and conflict messages', () => {
    fillValidForm();
    service.resolveIncident.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 403,
      error: { errorCode: 'ACCESS_DENIED' }
    })));
    component.submit();
    expect(component.errorMessage()).toBe(
      'You do not have permission to resolve this incident.'
    );

    service.resolveIncident.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { errorCode: 'INCIDENT_NOT_RESOLVABLE' }
    })));
    component.submit();
    expect(component.errorMessage()).toBe(
      'This incident cannot be resolved in its current state.'
    );
  });

  it('uses a safe generic message for unknown errors without exposing raw text', () => {
    service.resolveIncident.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 500,
      error: { errorCode: 'INTERNAL_ERROR', message: 'SQL stack trace and secret' }
    })));
    fillValidForm();

    component.submit();
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Incident could not be resolved. Please try again.');
    expect(fixture.nativeElement.textContent).not.toContain('SQL stack trace and secret');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('does not call the service when the dialog is cancelled', () => {
    const cancel = fixture.nativeElement.querySelector(
      'button[mat-dialog-close]'
    ) as HTMLButtonElement;
    cancel.click();

    expect(service.resolveIncident).not.toHaveBeenCalled();
  });
});
