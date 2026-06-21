import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let auth: AuthService;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    sessionStorage.setItem('incidentTriage.accessToken', 'test-token');
    sessionStorage.setItem('incidentTriage.user', JSON.stringify({
      id: 1, name: 'Support User 1', username: 'support1', role: 'SUPPORT_ENGINEER'
    }));
    sessionStorage.setItem('incidentTriage.expiresAt', JSON.stringify(Date.now() + 60_000));
    router = jasmine.createSpyObj<Router>('Router', ['navigate'], { url: '/dashboard' });
    router.navigate.and.resolveTo(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router }
      ]
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('adds the bearer token to protected API calls', () => {
    client.get('/api/incidents').subscribe();
    const request = http.expectOne('/api/incidents');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush({});
  });

  it('does not add the token to login', () => {
    client.post('/api/auth/login', {}).subscribe();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
  });

  it('ignores unrelated external URLs', () => {
    client.get('https://example.com/status').subscribe();
    const request = http.expectOne('https://example.com/status');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
  });

  it('clears the session and redirects on an API 401', () => {
    client.get('/api/incidents').subscribe({
      error: () => undefined
    });
    http.expectOne('/api/incidents').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isAuthenticated()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not clear the session on an API 403', () => {
    client.get('/api/incidents').subscribe({
      error: () => undefined
    });
    http.expectOne('/api/incidents').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(auth.isAuthenticated()).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('does not attach an expired token', () => {
    sessionStorage.setItem('incidentTriage.expiresAt', JSON.stringify(Date.now() - 1));
    auth.restoreSession();
    client.get('/api/incidents').subscribe();
    const request = http.expectOne('/api/incidents');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
    expect(sessionStorage.length).toBe(0);
  });

  it('does not redirect when the login endpoint returns 401', () => {
    client.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    http.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
