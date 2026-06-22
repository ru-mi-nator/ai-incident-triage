import { HttpErrorResponse } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { IncidentPage } from './incident.models';
import { IncidentListComponent } from './incident-list.component';
import { IncidentService } from './incident.service';

describe('IncidentListComponent', () => {
  let fixture: ComponentFixture<IncidentListComponent>;
  let component: IncidentListComponent;
  let queryParams: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let service: jasmine.SpyObj<IncidentService>;
  let router: jasmine.SpyObj<Router>;

  const populatedPage: IncidentPage = {
    content: [{
      id: 42,
      displayId: 'INC-0042',
      title: 'Login API returning 500',
      applicationName: 'AUTH_SERVICE',
      environment: 'PROD',
      status: 'IN_PROGRESS',
      priority: null,
      assignedDeveloper: null,
      createdAt: '2026-06-21T10:30:00Z'
    }],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true
  };

  async function configure(
    response: Observable<IncidentPage> = of(populatedPage),
    params: Record<string, string> = {}
  ): Promise<void> {
    queryParams = new BehaviorSubject(convertToParamMap(params));
    service = jasmine.createSpyObj<IncidentService>('IncidentService', ['getIncidents']);
    service.getIncidents.and.returnValue(response);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);

    await TestBed.configureTestingModule({
      imports: [IncidentListComponent],
      providers: [
        provideNoopAnimations(),
        { provide: IncidentService, useValue: service },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: queryParams.asObservable() }
        },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(IncidentListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => TestBed.resetTestingModule());

  it('loads page 0 with size 10 and renders backend data with safe labels', async () => {
    await configure();

    expect(service.getIncidents).toHaveBeenCalledOnceWith(0, 10, 'createdAt,desc');
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('INC-0042');
    expect(text).toContain('Login API returning 500');
    expect(text).toContain('Auth Service');
    expect(text).toContain('Prod');
    expect(text).toContain('In Progress');
    expect(text).toContain('Not assessed');
    expect(text).toContain('Unassigned');
  });

  it('requests the selected page and resets to page 0 when page size changes', async () => {
    await configure();

    component.onPage({ pageIndex: 2, pageSize: 10, length: 40, previousPageIndex: 1 });
    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
      queryParams: { page: 2, size: 10 }
    }));
    queryParams.next(convertToParamMap({ page: '2', size: '10' }));
    expect(service.getIncidents).toHaveBeenCalledWith(2, 10, 'createdAt,desc');

    component.onPage({ pageIndex: 2, pageSize: 20, length: 40, previousPageIndex: 1 });
    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
      queryParams: { page: 0, size: 20 }
    }));
    queryParams.next(convertToParamMap({ page: '0', size: '20' }));
    expect(service.getIncidents).toHaveBeenCalledWith(0, 20, 'createdAt,desc');
  });

  it('falls back safely for invalid query parameters', async () => {
    await configure(of(populatedPage), { page: '-4', size: '999' });

    expect(service.getIncidents).toHaveBeenCalledOnceWith(0, 10, 'createdAt,desc');
  });

  it('shows loading while a request is pending', async () => {
    const pending = new Subject<IncidentPage>();
    await configure(pending.asObservable());

    expect(component.loading()).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Loading incidents');
    pending.next(populatedPage);
    pending.complete();
  });

  it('shows an empty state for an empty page', async () => {
    await configure(of({ ...populatedPage, content: [], totalElements: 0 }));

    expect(fixture.nativeElement.textContent).toContain('No incidents yet');
  });

  it('shows a safe error and retry repeats the current request', async () => {
    await configure(throwError(() => new HttpErrorResponse({ status: 500 })), {
      page: '3',
      size: '20'
    });

    expect(fixture.nativeElement.textContent).toContain(
      'Incidents could not be loaded. Please try again.'
    );
    service.getIncidents.and.returnValue(of({ ...populatedPage, page: 3, size: 20 }));
    component.retry();
    expect(service.getIncidents).toHaveBeenCalledTimes(2);
    expect(service.getIncidents).toHaveBeenCalledWith(3, 20, 'createdAt,desc');
  });

  it('shows a safe access message for forbidden responses', async () => {
    await configure(throwError(() => new HttpErrorResponse({ status: 403 })));

    expect(fixture.nativeElement.textContent).toContain(
      'You do not have access to view incidents.'
    );
  });

  it('refreshes without resetting the current page', async () => {
    await configure(of({ ...populatedPage, page: 2, size: 20 }), {
      page: '2',
      size: '20'
    });

    component.refresh();
    expect(service.getIncidents).toHaveBeenCalledTimes(2);
    expect(service.getIncidents).toHaveBeenCalledWith(2, 20, 'createdAt,desc');
  });

  it('renders malformed timestamps safely', async () => {
    const incident = { ...populatedPage.content[0], createdAt: 'not-a-date' };
    await configure(of({ ...populatedPage, content: [incident] }));

    expect(fixture.nativeElement.textContent).toContain('Unavailable');
  });

  it('ignores an older response after route pagination starts a newer request', async () => {
    const older = new Subject<IncidentPage>();
    const newer = new Subject<IncidentPage>();
    await configure(older.asObservable());
    service.getIncidents.and.returnValue(newer.asObservable());

    queryParams.next(convertToParamMap({ page: '1', size: '10' }));
    newer.next({
      ...populatedPage,
      content: [{ ...populatedPage.content[0], id: 43, displayId: 'INC-0043', title: 'Newer page' }],
      page: 1
    });
    older.next(populatedPage);
    fixture.detectChanges();

    expect(component.incidents()[0].displayId).toBe('INC-0043');
    expect(component.loading()).toBeFalse();
  });

  it('redirects an out-of-range non-empty result set to the last valid page', async () => {
    await configure(of({
      ...populatedPage,
      content: [],
      page: 3,
      size: 10,
      totalElements: 15,
      totalPages: 2,
      first: false,
      last: true
    }), { page: '3', size: '10' });

    expect(router.navigate).toHaveBeenCalledWith([], jasmine.objectContaining({
      queryParams: { page: 1, size: 10 },
      replaceUrl: true
    }));
  });
});
