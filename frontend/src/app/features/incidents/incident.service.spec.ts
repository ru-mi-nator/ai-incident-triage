import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreateIncidentRequest, CreatedIncident, IncidentPage } from './incident.models';
import { IncidentService } from './incident.service';

describe('IncidentService', () => {
  let service: IncidentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(IncidentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends page, size, and sort parameters and returns a typed page', () => {
    const response: IncidentPage = {
      content: [],
      page: 2,
      size: 20,
      totalElements: 41,
      totalPages: 3,
      first: false,
      last: true
    };
    let actual: IncidentPage | undefined;

    service.getIncidents(2, 20, 'createdAt,desc').subscribe(value => actual = value);

    const request = http.expectOne(
      request => request.url === '/api/incidents'
        && request.params.get('page') === '2'
        && request.params.get('size') === '20'
        && request.params.get('sort') === 'createdAt,desc'
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.getAll('page')).toEqual(['2']);
    expect(request.request.params.getAll('size')).toEqual(['20']);
    expect(request.request.params.getAll('sort')).toEqual(['createdAt,desc']);
    request.flush(response);
    expect(actual).toEqual(response);
  });

  it('posts the exact create request without manually adding authentication', () => {
    const request: CreateIncidentRequest = {
      title: 'Payment API returning 500 errors',
      description: 'Users cannot complete payments.',
      applicationName: 'PAYMENT_SERVICE',
      environment: 'PROD',
      errorLogs: null
    };
    const response: CreatedIncident = {
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
    let actual: CreatedIncident | undefined;

    service.createIncident(request).subscribe(value => actual = value);

    const httpRequest = http.expectOne('/api/incidents');
    expect(httpRequest.request.method).toBe('POST');
    expect(httpRequest.request.body).toEqual(request);
    expect(httpRequest.request.headers.has('Authorization')).toBeFalse();
    httpRequest.flush(response);
    expect(actual).toEqual(response);
  });
});
