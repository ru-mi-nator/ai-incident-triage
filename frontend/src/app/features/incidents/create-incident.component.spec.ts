import { HttpErrorResponse } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { CreateIncidentComponent } from './create-incident.component';
import { CreateIncidentRequest, CreatedIncident } from './incident.models';
import { IncidentService } from './incident.service';

describe('CreateIncidentComponent', () => {
  let fixture: ComponentFixture<CreateIncidentComponent>;
  let component: CreateIncidentComponent;
  let service: jasmine.SpyObj<IncidentService>;
  let router: Router;
  let navigateSpy: jasmine.Spy;
  let snackBarOpenSpy: jasmine.Spy;

  const createdIncident: CreatedIncident = {
    id: 42,
    displayId: 'INC-0042',
    status: 'OPEN',
    createdBy: {
      id: 1,
      name: 'Support User',
      username: 'support1',
      role: 'SUPPORT_ENGINEER'
    },
    assignedDeveloper: null
  };

  beforeEach(async () => {
    service = jasmine.createSpyObj<IncidentService>('IncidentService', ['createIncident']);
    service.createIncident.and.returnValue(of(createdIncident));
    await TestBed.configureTestingModule({
      imports: [CreateIncidentComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: IncidentService, useValue: service }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(CreateIncidentComponent);
    component = fixture.componentInstance;
    snackBarOpenSpy = spyOn(fixture.debugElement.injector.get(MatSnackBar), 'open');
    fixture.detectChanges();
  });

  function setValidForm(overrides: Partial<{
    title: string;
    description: string;
    applicationName: 'AUTH_SERVICE' | 'PAYMENT_SERVICE';
    environment: 'QA' | 'PROD';
    errorLogs: string;
  }> = {}): void {
    component.createForm.setValue({
      title: overrides.title ?? 'Payment API returning 500',
      description: overrides.description ?? 'Users cannot complete payments.',
      applicationName: overrides.applicationName ?? 'PAYMENT_SERVICE',
      environment: overrides.environment ?? 'PROD',
      errorLogs: overrides.errorLogs ?? 'Stack trace'
    });
  }

  it('requires all mandatory fields and rejects whitespace-only text', () => {
    component.submit();
    expect(service.createIncident).not.toHaveBeenCalled();

    component.createForm.patchValue({
      title: '   ',
      description: '\n  ',
      applicationName: 'AUTH_SERVICE',
      environment: 'QA'
    });

    expect(component.createForm.controls.title.hasError('blank')).toBeTrue();
    expect(component.createForm.controls.description.hasError('blank')).toBeTrue();
    component.submit();
    expect(service.createIncident).not.toHaveBeenCalled();
  });

  it('enforces title, description, and error-log maximum lengths', () => {
    setValidForm({
      title: 'x'.repeat(151),
      description: 'y'.repeat(2001),
      errorLogs: 'z'.repeat(10001)
    });

    expect(component.createForm.controls.title.hasError('maxlength')).toBeTrue();
    expect(component.createForm.controls.description.hasError('maxlength')).toBeTrue();
    expect(component.createForm.controls.errorLogs.hasError('maxlength')).toBeTrue();
    component.submit();
    expect(service.createIncident).not.toHaveBeenCalled();
  });

  it('validates length after trimming surrounding whitespace', () => {
    setValidForm({
      title: `  ${'x'.repeat(150)}  `,
      description: `\n${'y'.repeat(2000)}\n`,
      errorLogs: `  ${'z'.repeat(10000)}  `
    });

    expect(component.createForm.valid).toBeTrue();
    expect(component.characterCount(component.createForm.controls.title.value)).toBe(150);
    component.submit();
    expect(service.createIncident.calls.mostRecent().args[0].title).toBe('x'.repeat(150));
  });

  it('trims text, submits raw enum values, and converts blank logs to null', () => {
    setValidForm({
      title: '  Payment API returning 500  ',
      description: '  Users cannot complete payments.  ',
      applicationName: 'PAYMENT_SERVICE',
      environment: 'PROD',
      errorLogs: '   '
    });

    component.submit();

    const expected: CreateIncidentRequest = {
      title: 'Payment API returning 500',
      description: 'Users cannot complete payments.',
      applicationName: 'PAYMENT_SERVICE',
      environment: 'PROD',
      errorLogs: null
    };
    expect(service.createIncident).toHaveBeenCalledOnceWith(expected);
  });

  it('trims supplied error logs', () => {
    setValidForm({ errorLogs: '  Stack trace line  ' });

    component.submit();

    expect(service.createIncident.calls.mostRecent().args[0].errorLogs).toBe('Stack trace line');
  });

  it('prevents duplicate submissions while the request is pending', () => {
    const pending = new Subject<CreatedIncident>();
    service.createIncident.and.returnValue(pending.asObservable());
    setValidForm();

    component.submit();
    component.submit();

    expect(service.createIncident).toHaveBeenCalledTimes(1);
    expect(component.submitting()).toBeTrue();
    pending.next(createdIncident);
    pending.complete();
    expect(component.submitting()).toBeFalse();
  });

  it('shows a snackbar and navigates to the first list page after creation', () => {
    setValidForm();

    component.submit();

    expect(snackBarOpenSpy).toHaveBeenCalledWith(
      'Incident INC-0042 created successfully',
      'Dismiss',
      { duration: 5000 }
    );
    expect(navigateSpy).toHaveBeenCalledOnceWith(
      ['/incidents'],
      { queryParams: { page: 0, size: 10 } }
    );
  });

  it('shows a safe permission error without clearing form data', () => {
    service.createIncident.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 403, error: { message: 'Internal details' } })
    ));
    setValidForm();

    component.submit();

    expect(component.errorMessage()).toBe('You do not have permission to create incidents.');
    expect(component.createForm.controls.title.value).toBe('Payment API returning 500');
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('maps only recognized validation fields and preserves entered data', () => {
    service.createIncident.and.returnValue(throwError(() =>
      new HttpErrorResponse({
        status: 400,
        error: {
          errorCode: 'VALIDATION_FAILED',
          fieldErrors: { title: 'Rejected', unknownField: 'Do not map' }
        }
      })
    ));
    setValidForm();

    component.submit();

    expect(component.errorMessage()).toBe('Please review the highlighted fields.');
    expect(component.createForm.controls.title.hasError('server')).toBeTrue();
    expect(component.createForm.controls.description.valid).toBeTrue();
    expect(component.createForm.controls.title.value).toBe('Payment API returning 500');

    component.createForm.controls.description.setValue('Updated description');
    expect(component.createForm.controls.title.hasError('server')).toBeTrue();
    component.createForm.controls.title.setValue('Updated title');
    expect(component.createForm.controls.title.hasError('server')).toBeFalse();
  });

  it('shows a generic safe message for network and server failures', () => {
    service.createIncident.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 500, error: { message: 'SQL stack trace' } })
    ));
    setValidForm();

    component.submit();

    expect(component.errorMessage()).toBe('Incident could not be created. Please try again.');
    expect(component.errorMessage()).not.toContain('SQL');
    expect(component.submitting()).toBeFalse();
  });

  it('renders readable enum labels and a cancel link to incidents', () => {
    const text = fixture.nativeElement.textContent as string;
    const cancel = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;

    expect(component.applicationLabel('AUTH_SERVICE')).toBe('Auth Service');
    expect(component.environmentLabel('DEV')).toBe('Development');
    expect(component.environmentLabel('PROD')).toBe('Production');
    expect(text).toContain('Create Incident');
    expect(cancel.textContent).toContain('Cancel');
    expect(cancel.getAttribute('href')).toBe('/incidents');
  });
});
