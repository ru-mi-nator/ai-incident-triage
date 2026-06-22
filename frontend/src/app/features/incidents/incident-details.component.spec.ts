import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, Observable, of, Subject, throwError } from 'rxjs';
import { IncidentDetailsComponent } from './incident-details.component';
import { IncidentDetails } from './incident.models';
import { IncidentService } from './incident.service';

describe('IncidentDetailsComponent', () => {
  let fixture: ComponentFixture<IncidentDetailsComponent>;
  let component: IncidentDetailsComponent;
  let paramMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let queryParamMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let service: jasmine.SpyObj<IncidentService>;

  const incident: IncidentDetails = {
    id: 42,
    displayId: 'INC-0042',
    title: 'Login API returning 500',
    description: 'Users are unable to log in after deployment.',
    applicationName: 'AUTH_SERVICE',
    environment: 'PROD',
    errorLogs: '<script>alert("unsafe")</script>\nNullPointerException',
    status: 'IN_PROGRESS',
    createdBy: {
      id: 1,
      name: 'Support User',
      username: 'support1',
      role: 'SUPPORT_ENGINEER'
    },
    assignedDeveloper: null,
    assignedAt: null,
    finalCategory: null,
    finalPriority: null,
    actualRootCause: null,
    actualResolution: null,
    resolvedAt: null,
    createdAt: '2026-06-21T10:30:00Z',
    updatedAt: '2026-06-21T10:35:00Z',
    aiAnalysis: null
  };

  async function configure(
    response: Observable<IncidentDetails> = of(incident),
    id: string | null = '42',
    query: Record<string, string> = {}
  ): Promise<void> {
    paramMap = new BehaviorSubject(convertToParamMap(id === null ? {} : { id }));
    queryParamMap = new BehaviorSubject(convertToParamMap(query));
    service = jasmine.createSpyObj<IncidentService>('IncidentService', ['getIncidentById']);
    service.getIncidentById.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [IncidentDetailsComponent],
      providers: [
        provideNoopAnimations(),
        { provide: IncidentService, useValue: service },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: paramMap.asObservable(),
            queryParamMap: queryParamMap.asObservable()
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(IncidentDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => TestBed.resetTestingModule());

  it('loads a valid ID once and renders the core incident safely', async () => {
    await configure();

    expect(service.getIncidentById).toHaveBeenCalledOnceWith(42);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('INC-0042');
    expect(text).toContain('Login API returning 500');
    expect(text).toContain(incident.description);
    expect(text).toContain('NullPointerException');
    expect(text).toContain('<script>alert("unsafe")</script>');
    expect(fixture.nativeElement.querySelector('script')).toBeNull();
    expect(text).toContain('Support User · Support Engineer');
    expect(text).toContain('Unassigned');
    expect(text).toContain('Auth Service');
    expect(text).toContain('In Progress');
    expect(text).toContain('Priority: Not assessed');
  });

  for (const invalidId of [null, '', '0', '-1', 'abc', '1.5']) {
    it(`does not request invalid route ID ${invalidId ?? 'missing'}`, async () => {
      await configure(of(incident), invalidId);

      expect(service.getIncidentById).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).toContain('Incident not found.');
    });
  }

  it('handles missing logs and missing AI analysis cleanly', async () => {
    await configure(of({ ...incident, errorLogs: null }));

    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Error logs');
    expect(text).toContain('No AI analysis has been generated.');
  });

  it('renders all existing AI analysis fields', async () => {
    await configure(of({
      ...incident,
      aiAnalysis: {
        suggestedCategory: 'AUTHENTICATION',
        suggestedPriority: 'HIGH',
        probableRootCause: 'Deployment configuration drift.',
        suggestedResolution: 'Correct configuration and restart.',
        modelName: 'openai-model',
        generatedAt: '2026-06-21T10:40:00Z'
      }
    }));

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Advisory AI output for human review.');
    expect(text).toContain('Authentication');
    expect(text).toContain('High');
    expect(text).toContain('Deployment configuration drift.');
    expect(text).toContain('Correct configuration and restart.');
    expect(text).toContain('openai-model');
  });

  it('renders a malformed AI generated timestamp as unavailable', async () => {
    await configure(of({
      ...incident,
      aiAnalysis: {
        suggestedCategory: 'AUTHENTICATION',
        suggestedPriority: 'HIGH',
        probableRootCause: 'Deployment configuration drift.',
        suggestedResolution: 'Correct configuration and restart.',
        modelName: 'openai-model',
        generatedAt: 'not-a-date'
      }
    }));

    expect(fixture.nativeElement.textContent).toContain('Unavailable');
  });

  it('renders final resolution only when final data exists', async () => {
    await configure(of({
      ...incident,
      status: 'RESOLVED',
      finalCategory: 'CONFIGURATION',
      finalPriority: 'CRITICAL',
      actualRootCause: 'An invalid production setting.',
      actualResolution: 'Corrected and redeployed.',
      resolvedAt: '2026-06-21T11:00:00Z'
    }));

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Final resolution');
    expect(text).toContain('Human-entered final values are authoritative.');
    expect(text).toContain('Configuration');
    expect(text).toContain('Critical');
    expect(text).toContain('An invalid production setting.');
    expect(text).toContain('Corrected and redeployed.');
  });

  it('does not render final-resolution content for an unresolved incident', async () => {
    await configure();

    expect(fixture.nativeElement.textContent).not.toContain('Final resolution');
    expect(fixture.nativeElement.textContent).not.toContain('Resolve Incident');
    expect(fixture.nativeElement.textContent).not.toContain('Analyze');
  });

  it('does not present partial final data as an authoritative resolution', async () => {
    await configure(of({
      ...incident,
      status: 'RESOLVED',
      finalCategory: 'CONFIGURATION'
    }));

    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Final resolution');
    expect(text).not.toContain('Human-entered final values are authoritative.');
  });

  it('shows the safe not-found state for a 404', async () => {
    await configure(throwError(() => new HttpErrorResponse({
      status: 404,
      error: { errorCode: 'INCIDENT_NOT_FOUND' }
    })));

    expect(fixture.nativeElement.textContent).toContain('Incident not found.');
  });

  it('shows a safe access message for a 403', async () => {
    await configure(throwError(() => new HttpErrorResponse({ status: 403 })));

    expect(fixture.nativeElement.textContent).toContain(
      'You do not have permission to view this incident.'
    );
  });

  it('shows a generic network error and retries the same incident', async () => {
    await configure(throwError(() => new HttpErrorResponse({ status: 500 })));

    expect(fixture.nativeElement.textContent).toContain(
      'Incident details could not be loaded. Please try again.'
    );
    service.getIncidentById.and.returnValue(of(incident));
    component.retry();
    fixture.detectChanges();
    expect(service.getIncidentById).toHaveBeenCalledTimes(2);
    expect(service.getIncidentById).toHaveBeenCalledWith(42);
    expect(fixture.nativeElement.textContent).toContain('INC-0042');
  });

  it('cancels an older request when the route ID changes', async () => {
    const older = new Subject<IncidentDetails>();
    const newer = new Subject<IncidentDetails>();
    await configure(older.asObservable());
    service.getIncidentById.and.returnValue(newer.asObservable());

    paramMap.next(convertToParamMap({ id: '43' }));
    newer.next({ ...incident, id: 43, displayId: 'INC-0043', title: 'Newer incident' });
    older.next(incident);
    fixture.detectChanges();

    expect(service.getIncidentById).toHaveBeenCalledTimes(2);
    expect(service.getIncidentById).toHaveBeenCalledWith(43);
    expect(fixture.nativeElement.textContent).toContain('INC-0043');
    expect(fixture.nativeElement.textContent).not.toContain('INC-0042');
  });

  it('clears previous incident content when a later navigation fails', async () => {
    await configure();
    service.getIncidentById.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 404 }))
    );

    paramMap.next(convertToParamMap({ id: '999' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Incident not found.');
    expect(fixture.nativeElement.textContent).not.toContain('INC-0042');
  });

  it('preserves valid list pagination in the back link', async () => {
    await configure(of(incident), '42', { page: '3', size: '20' });

    const link = fixture.nativeElement.querySelector('.back-link') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/incidents?page=3&size=20');
  });

  it('falls back safely for invalid list pagination', async () => {
    await configure(of(incident), '42', { page: '-2', size: '999' });

    const link = fixture.nativeElement.querySelector('.back-link') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/incidents?page=0&size=10');
  });

  it('renders malformed timestamps as unavailable', async () => {
    await configure(of({ ...incident, createdAt: 'bad-date', updatedAt: '' }));

    expect(fixture.nativeElement.textContent).toContain('Unavailable');
  });
});
