import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, Observable, of, Subject, throwError } from 'rxjs';
import { AuthenticatedUser } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { IncidentDetailsComponent } from './incident-details.component';
import { IncidentDetails } from './incident.models';
import { IncidentService } from './incident.service';
import { ResolveIncidentDialogComponent } from './resolve-incident-dialog.component';

describe('IncidentDetailsComponent', () => {
  let fixture: ComponentFixture<IncidentDetailsComponent>;
  let component: IncidentDetailsComponent;
  let paramMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let queryParamMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let service: jasmine.SpyObj<IncidentService>;
  let currentUser: AuthenticatedUser | null;
  let snackBarOpenSpy: jasmine.Spy;

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
  const supportUser: AuthenticatedUser = {
    id: 1,
    name: 'Support User',
    username: 'support1',
    role: 'SUPPORT_ENGINEER'
  };
  const developerUser: AuthenticatedUser = {
    id: 2,
    name: 'Developer One',
    username: 'developer1',
    role: 'DEVELOPER'
  };

  function button(label: string): HTMLButtonElement | undefined {
    return Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>
    ).find(candidate => candidate.textContent?.replace(/\s+/g, ' ').trim() === label);
  }

  async function configure(
    response: Observable<IncidentDetails> = of(incident),
    id: string | null = '42',
    query: Record<string, string> = {},
    user: AuthenticatedUser | null = null
  ): Promise<void> {
    currentUser = user;
    paramMap = new BehaviorSubject(convertToParamMap(id === null ? {} : { id }));
    queryParamMap = new BehaviorSubject(convertToParamMap(query));
    service = jasmine.createSpyObj<IncidentService>('IncidentService', [
      'getIncidentById',
      'assignToMe',
      'analyzeIncident',
      'resolveIncident'
    ]);
    service.getIncidentById.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [IncidentDetailsComponent],
      providers: [
        provideNoopAnimations(),
        { provide: IncidentService, useValue: service },
        { provide: AuthService, useValue: { currentUser: () => currentUser } },
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
    snackBarOpenSpy = spyOn(fixture.debugElement.injector.get(MatSnackBar), 'open');
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

  it('shows Assign only to a developer for an unassigned OPEN incident', async () => {
    const openIncident = { ...incident, status: 'OPEN' as const };
    await configure(of(openIncident), '42', {}, developerUser);
    expect(button('Assign to me')).toBeDefined();

    currentUser = supportUser;
    fixture.detectChanges();
    expect(button('Assign to me')).toBeUndefined();
  });

  it('hides every action and leaves action state idle when auth state is missing', async () => {
    const openIncident = { ...incident, status: 'OPEN' as const };
    await configure(of(openIncident), '42', {}, null);

    expect(fixture.nativeElement.textContent).not.toContain('Incident actions');
    component.assignToMe();
    component.analyzeIncident();
    component.openResolveDialog();
    expect(component.activeAction()).toBeNull();
    expect(service.assignToMe).not.toHaveBeenCalled();
    expect(service.analyzeIncident).not.toHaveBeenCalled();
  });

  it('hides Assign when the incident is assigned or not OPEN', async () => {
    await configure(of({
      ...incident,
      status: 'OPEN',
      assignedDeveloper: { ...developerUser }
    }), '42', {}, developerUser);

    expect(button('Assign to me')).toBeUndefined();
    expect(component.canAssign({ ...incident, status: 'IN_PROGRESS' })).toBeFalse();
    expect(component.canAssign({ ...incident, status: 'RESOLVED' })).toBeFalse();
  });

  it('shows Analyze only to the eligible creator or assigned developer', async () => {
    await configure(of({ ...incident, status: 'OPEN' }), '42', {}, supportUser);
    expect(button('Generate AI Analysis')).toBeDefined();

    currentUser = { ...supportUser, id: 99 };
    fixture.detectChanges();
    expect(button('Generate AI Analysis')).toBeUndefined();

    currentUser = developerUser;
    component.incident.set({
      ...incident,
      assignedDeveloper: { ...developerUser },
      status: 'IN_PROGRESS'
    });
    fixture.detectChanges();
    expect(button('Generate AI Analysis')).toBeDefined();

    currentUser = { ...developerUser, id: 3 };
    fixture.detectChanges();
    expect(button('Generate AI Analysis')).toBeUndefined();
  });

  it('hides Analyze when analysis exists or the incident is resolved', async () => {
    await configure(of({ ...incident, status: 'OPEN' }), '42', {}, supportUser);
    const analysis = {
      suggestedCategory: 'API' as const,
      suggestedPriority: 'HIGH' as const,
      probableRootCause: 'Cause',
      suggestedResolution: 'Resolution',
      modelName: 'model',
      generatedAt: '2026-06-21T10:40:00Z'
    };

    expect(component.canAnalyze({ ...incident, status: 'OPEN', aiAnalysis: analysis })).toBeFalse();
    expect(component.canAnalyze({ ...incident, status: 'RESOLVED' })).toBeFalse();
  });

  it('shows Resolve only to the assigned developer for IN_PROGRESS', async () => {
    await configure(of({
      ...incident,
      assignedDeveloper: { ...developerUser }
    }), '42', {}, developerUser);

    expect(button('Resolve Incident')).toBeDefined();
    currentUser = supportUser;
    fixture.detectChanges();
    expect(button('Resolve Incident')).toBeUndefined();
    currentUser = { ...developerUser, id: 3 };
    expect(component.canResolve({ ...incident, assignedDeveloper: { ...developerUser } })).toBeFalse();
    currentUser = developerUser;
    expect(component.canResolve({
      ...incident,
      status: 'OPEN',
      assignedDeveloper: { ...developerUser }
    })).toBeFalse();
    expect(component.canResolve({
      ...incident,
      status: 'RESOLVED',
      assignedDeveloper: { ...developerUser }
    })).toBeFalse();
  });

  it('uses strict numeric identity matching for owned developer actions', async () => {
    await configure(of({
      ...incident,
      assignedDeveloper: {
        ...developerUser,
        id: '2' as unknown as number
      }
    }), '42', {}, developerUser);

    expect(component.canAnalyze(component.incident()!)).toBeFalse();
    expect(component.canResolve(component.incident()!)).toBeFalse();
  });

  it('assigns once, blocks duplicate clicks, updates details, and shows success', async () => {
    const pending = new Subject<IncidentDetails>();
    const openIncident = { ...incident, status: 'OPEN' as const };
    const assigned = {
      ...openIncident,
      status: 'IN_PROGRESS' as const,
      assignedDeveloper: { ...developerUser }
    };
    await configure(of(openIncident), '42', {}, developerUser);
    service.assignToMe.and.returnValue(pending.asObservable());

    component.assignToMe();
    component.assignToMe();
    expect(service.assignToMe).toHaveBeenCalledOnceWith(42);
    expect(component.assigning()).toBeTrue();

    pending.next(assigned);
    pending.complete();
    fixture.detectChanges();
    expect(component.incident()).toEqual(assigned);
    expect(fixture.nativeElement.textContent).toContain('In Progress');
    expect(fixture.nativeElement.textContent).toContain('Developer One');
    expect(snackBarOpenSpy).toHaveBeenCalledWith(
      'Incident assigned to you.',
      'Dismiss',
      { duration: 5000 }
    );
  });

  it('shows safe assignment errors and preserves existing details', async () => {
    const openIncident = { ...incident, status: 'OPEN' as const };
    await configure(of(openIncident), '42', {}, developerUser);
    service.assignToMe.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { errorCode: 'INCIDENT_ALREADY_ASSIGNED', message: 'raw backend detail' }
    })));

    component.assignToMe();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'This incident has already been assigned.'
    );
    expect(fixture.nativeElement.textContent).not.toContain('raw backend detail');
    expect(fixture.nativeElement.textContent).toContain('INC-0042');
    expect(component.assigning()).toBeFalse();
  });

  for (const [errorCode, message] of [
    ['INCIDENT_NOT_OPEN', 'Only open incidents can be assigned.'],
    ['ACCESS_DENIED', 'You do not have permission to assign this incident.']
  ] as const) {
    it(`maps assignment error ${errorCode} safely`, async () => {
      await configure(of({ ...incident, status: 'OPEN' }), '42', {}, developerUser);
      service.assignToMe.and.returnValue(throwError(() => new HttpErrorResponse({
        status: errorCode === 'ACCESS_DENIED' ? 403 : 409,
        error: { errorCode, message: 'unsafe raw message' }
      })));

      component.assignToMe();

      expect(component.actionError()).toBe(message);
      expect(fixture.nativeElement.textContent).not.toContain('unsafe raw message');
    });
  }

  it('generates analysis once with loading state and updates the AI section', async () => {
    const pending = new Subject<IncidentDetails>();
    const openIncident = { ...incident, status: 'OPEN' as const };
    const analyzed = {
      ...openIncident,
      aiAnalysis: {
        suggestedCategory: 'AUTHENTICATION' as const,
        suggestedPriority: 'HIGH' as const,
        probableRootCause: 'Configuration drift.',
        suggestedResolution: 'Correct the setting.',
        modelName: 'model',
        generatedAt: '2026-06-21T10:40:00Z'
      }
    };
    await configure(of(openIncident), '42', {}, supportUser);
    service.analyzeIncident.and.returnValue(pending.asObservable());

    component.analyzeIncident();
    component.analyzeIncident();
    fixture.detectChanges();
    expect(service.analyzeIncident).toHaveBeenCalledOnceWith(42);
    expect(fixture.nativeElement.textContent).toContain('Generating analysis...');

    pending.next(analyzed);
    pending.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Configuration drift.');
    expect(button('Generate AI Analysis')).toBeUndefined();
    expect(snackBarOpenSpy).toHaveBeenCalledWith(
      'AI analysis generated.',
      'Dismiss',
      { duration: 5000 }
    );
  });

  it('shows the safe 503 analysis message and keeps details visible', async () => {
    await configure(of({ ...incident, status: 'OPEN' }), '42', {}, supportUser);
    service.analyzeIncident.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 503,
      error: { errorCode: 'AI_SERVICE_UNAVAILABLE', message: 'provider secret' }
    })));

    component.analyzeIncident();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'AI analysis is temporarily unavailable. Please try again later.'
    );
    expect(fixture.nativeElement.textContent).not.toContain('provider secret');
    expect(fixture.nativeElement.textContent).toContain('INC-0042');
  });

  for (const [errorCode, message] of [
    ['AI_ANALYSIS_ALREADY_EXISTS', 'AI analysis already exists for this incident.'],
    ['INCIDENT_NOT_ANALYZABLE', 'This incident cannot be analyzed in its current state.'],
    ['ACCESS_DENIED', 'You do not have permission to analyze this incident.']
  ] as const) {
    it(`maps analysis error ${errorCode} safely`, async () => {
      await configure(of({ ...incident, status: 'OPEN' }), '42', {}, supportUser);
      service.analyzeIncident.and.returnValue(throwError(() => new HttpErrorResponse({
        status: errorCode === 'ACCESS_DENIED' ? 403 : 409,
        error: { errorCode, message: 'unsafe provider detail' }
      })));

      component.analyzeIncident();

      expect(component.actionError()).toBe(message);
      expect(fixture.nativeElement.textContent).not.toContain('unsafe provider detail');
    });
  }

  it('opens the resolution dialog only for an eligible developer', async () => {
    await configure(of({
      ...incident,
      assignedDeveloper: { ...developerUser }
    }), '42', {}, developerUser);
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const openSpy = spyOn(dialog, 'open').and.callThrough();

    component.openResolveDialog();
    expect(openSpy).toHaveBeenCalledTimes(1);
    dialog.closeAll();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.activeAction()).toBeNull();

    currentUser = supportUser;
    component.openResolveDialog();
    expect(openSpy).toHaveBeenCalledTimes(1);
    expect(component.activeAction()).toBeNull();
  });

  it('allows only one conflicting page action at a time', async () => {
    const pending = new Subject<IncidentDetails>();
    await configure(of({
      ...incident,
      assignedDeveloper: { ...developerUser }
    }), '42', {}, developerUser);
    service.analyzeIncident.and.returnValue(pending.asObservable());
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const openSpy = spyOn(dialog, 'open').and.callThrough();

    component.analyzeIncident();
    component.openResolveDialog();

    expect(service.analyzeIncident).toHaveBeenCalledOnceWith(42);
    expect(openSpy).not.toHaveBeenCalled();
    expect(component.activeAction()).toBe('analyze');
    pending.complete();
    expect(component.activeAction()).toBeNull();
  });

  it('applies a successful resolution once and hides all lifecycle actions', async () => {
    const closed = new Subject<IncidentDetails | undefined>();
    const fakeRef = jasmine.createSpyObj<
      MatDialogRef<ResolveIncidentDialogComponent, IncidentDetails>
    >('MatDialogRef', ['close', 'afterClosed']);
    fakeRef.afterClosed.and.returnValue(closed.asObservable());
    await configure(of({
      ...incident,
      assignedDeveloper: { ...developerUser }
    }), '42', {}, developerUser);
    const configuredDialog = fixture.debugElement.injector.get(MatDialog);
    spyOn(configuredDialog, 'open').and.returnValue(fakeRef);
    const resolved: IncidentDetails = {
      ...incident,
      status: 'RESOLVED',
      assignedDeveloper: { ...developerUser },
      finalCategory: 'DATABASE',
      finalPriority: 'HIGH',
      actualRootCause: 'Connection pool exhaustion.',
      actualResolution: 'Closed leaked connections.',
      resolvedAt: '2026-06-21T11:00:00Z'
    };

    component.openResolveDialog();
    closed.next(resolved);
    closed.complete();
    fixture.detectChanges();

    expect(component.incident()).toEqual(resolved);
    expect(fixture.nativeElement.textContent).toContain('Final resolution');
    expect(fixture.nativeElement.textContent).not.toContain('Incident actions');
    expect(snackBarOpenSpy).toHaveBeenCalledOnceWith(
      'Incident resolved successfully.',
      'Dismiss',
      { duration: 5000 }
    );
  });

  it('closes the resolution dialog and ignores its result after route navigation', async () => {
    const closed = new Subject<IncidentDetails | undefined>();
    const fakeRef = jasmine.createSpyObj<
      MatDialogRef<ResolveIncidentDialogComponent, IncidentDetails>
    >('MatDialogRef', ['close', 'afterClosed']);
    fakeRef.afterClosed.and.returnValue(closed.asObservable());
    await configure(of({
      ...incident,
      assignedDeveloper: { ...developerUser }
    }), '42', {}, developerUser);
    const dialog = fixture.debugElement.injector.get(MatDialog);
    spyOn(dialog, 'open').and.returnValue(fakeRef);
    service.getIncidentById.and.returnValue(of({
      ...incident,
      id: 43,
      displayId: 'INC-0043',
      title: 'New incident'
    }));

    component.openResolveDialog();
    paramMap.next(convertToParamMap({ id: '43' }));
    closed.next({
      ...incident,
      status: 'RESOLVED',
      assignedDeveloper: { ...developerUser },
      finalCategory: 'DATABASE',
      finalPriority: 'HIGH',
      actualRootCause: 'Old route cause.',
      actualResolution: 'Old route resolution.',
      resolvedAt: '2026-06-21T11:00:00Z'
    });
    fixture.detectChanges();

    expect(fakeRef.close).toHaveBeenCalled();
    expect(component.activeAction()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('INC-0043');
    expect(fixture.nativeElement.textContent).not.toContain('Old route cause.');
    expect(snackBarOpenSpy).not.toHaveBeenCalledWith(
      'Incident resolved successfully.',
      'Dismiss',
      { duration: 5000 }
    );
  });

  it('ignores an action response after navigation to another incident', async () => {
    const pending = new Subject<IncidentDetails>();
    const openIncident = { ...incident, status: 'OPEN' as const };
    await configure(of(openIncident), '42', {}, developerUser);
    service.assignToMe.and.returnValue(pending.asObservable());
    service.getIncidentById.and.returnValue(of({
      ...incident,
      id: 43,
      displayId: 'INC-0043',
      title: 'New incident'
    }));

    component.assignToMe();
    paramMap.next(convertToParamMap({ id: '43' }));
    pending.next({
      ...openIncident,
      status: 'IN_PROGRESS',
      assignedDeveloper: { ...developerUser }
    });
    fixture.detectChanges();

    expect(component.activeAction()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('INC-0043');
    expect(fixture.nativeElement.textContent).not.toContain('Developer One');
  });

  it('ignores an analysis response after navigation to another incident', async () => {
    const pending = new Subject<IncidentDetails>();
    const openIncident = { ...incident, status: 'OPEN' as const };
    await configure(of(openIncident), '42', {}, supportUser);
    service.analyzeIncident.and.returnValue(pending.asObservable());
    service.getIncidentById.and.returnValue(of({
      ...incident,
      id: 43,
      displayId: 'INC-0043',
      title: 'New incident'
    }));

    component.analyzeIncident();
    paramMap.next(convertToParamMap({ id: '43' }));
    pending.next({
      ...openIncident,
      aiAnalysis: {
        suggestedCategory: 'API',
        suggestedPriority: 'HIGH',
        probableRootCause: 'Old route analysis.',
        suggestedResolution: 'Old route resolution.',
        modelName: 'model',
        generatedAt: '2026-06-21T10:40:00Z'
      }
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('INC-0043');
    expect(fixture.nativeElement.textContent).not.toContain('Old route analysis.');
    expect(snackBarOpenSpy).not.toHaveBeenCalled();
  });

  it('rejects an action response carrying a different incident ID', async () => {
    const pending = new Subject<IncidentDetails>();
    const openIncident = { ...incident, status: 'OPEN' as const };
    await configure(of(openIncident), '42', {}, developerUser);
    service.assignToMe.and.returnValue(pending.asObservable());

    component.assignToMe();
    pending.next({
      ...openIncident,
      id: 99,
      displayId: 'INC-0099',
      status: 'IN_PROGRESS',
      assignedDeveloper: { ...developerUser }
    });
    pending.complete();
    fixture.detectChanges();

    expect(component.incident()).toEqual(openIncident);
    expect(fixture.nativeElement.textContent).toContain('INC-0042');
    expect(fixture.nativeElement.textContent).not.toContain('INC-0099');
    expect(snackBarOpenSpy).not.toHaveBeenCalled();
  });

  it('prevents an older details response from overwriting an action result', async () => {
    const action = new Subject<IncidentDetails>();
    const staleDetails = new Subject<IncidentDetails>();
    const openIncident = { ...incident, status: 'OPEN' as const };
    const assigned = {
      ...openIncident,
      status: 'IN_PROGRESS' as const,
      assignedDeveloper: { ...developerUser }
    };
    await configure(of(openIncident), '42', {}, developerUser);
    service.assignToMe.and.returnValue(action.asObservable());
    service.getIncidentById.and.returnValue(staleDetails.asObservable());

    component.assignToMe();
    component.retry();
    action.next(assigned);
    action.complete();
    staleDetails.next(openIncident);
    fixture.detectChanges();

    expect(component.incident()).toEqual(assigned);
    expect(fixture.nativeElement.textContent).toContain('In Progress');
  });
});
