import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { LoginResponse } from './auth.models';

const TOKEN_KEY = 'incidentTriage.accessToken';
const USER_KEY = 'incidentTriage.user';
const EXPIRY_KEY = 'incidentTriage.expiresAt';
const response: LoginResponse = {
  accessToken: 'test-token',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: { id: 1, name: 'Support User 1', username: 'support1', role: 'SUPPORT_ENGINEER' }
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('stores token, safe user, and calculated expiry after login', () => {
    const before = Date.now();
    service.login('support1', 'Support@123').subscribe();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ username: 'support1', password: 'Support@123' });
    request.flush(response);

    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('test-token');
    expect(JSON.parse(sessionStorage.getItem(USER_KEY) ?? '{}')).toEqual(response.user);
    const expiresAt = Number(JSON.parse(sessionStorage.getItem(EXPIRY_KEY) ?? '0'));
    expect(expiresAt).toBeGreaterThanOrEqual(before + 3_600_000);
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('never stores the password or login request', () => {
    service.login('support1', 'Support@123').subscribe();
    http.expectOne('/api/auth/login').flush(response);
    const stored = Object.keys(sessionStorage)
      .map((key) => `${key}:${sessionStorage.getItem(key)}`)
      .join('|');
    expect(stored).not.toContain('Support@123');
    expect(stored).not.toContain('"password"');
  });

  it('rejects and clears an expired session', () => {
    seedSession(Date.now() - 1);
    service.restoreSession();
    expect(service.hasValidSession()).toBeFalse();
    expect(sessionStorage.length).toBe(0);
  });

  it('clears malformed session data', () => {
    sessionStorage.setItem(TOKEN_KEY, 'test-token');
    sessionStorage.setItem(USER_KEY, '{bad-json');
    sessionStorage.setItem(EXPIRY_KEY, JSON.stringify(Date.now() + 60_000));
    service.restoreSession();
    expect(sessionStorage.length).toBe(0);
  });

  it('clears a session with an invalid expiry value', () => {
    sessionStorage.setItem(TOKEN_KEY, 'test-token');
    sessionStorage.setItem(USER_KEY, JSON.stringify(response.user));
    sessionStorage.setItem(EXPIRY_KEY, JSON.stringify('tomorrow'));
    service.restoreSession();
    expect(sessionStorage.length).toBe(0);
  });

  it('clears a session with invalid user data', () => {
    sessionStorage.setItem(TOKEN_KEY, 'test-token');
    sessionStorage.setItem(USER_KEY, JSON.stringify({ ...response.user, role: 'ADMIN' }));
    sessionStorage.setItem(EXPIRY_KEY, JSON.stringify(Date.now() + 60_000));
    service.restoreSession();
    expect(sessionStorage.length).toBe(0);
  });

  it('restores a valid authenticated session as on page refresh', () => {
    seedSession(Date.now() + 60_000);
    service.restoreSession();
    expect(service.isAuthenticated()).toBeTrue();
    expect(service.currentUser()).toEqual(response.user);
    expect(service.currentToken()).toBe('test-token');
  });

  it('prevents a duplicate login while the first request is pending', () => {
    service.login('support1', 'Support@123').subscribe();
    expect(() => service.login('support1', 'Support@123')).toThrowError();
    http.expectOne('/api/auth/login').flush(response);
  });

  it('rejects an invalid login response without storing session data', () => {
    service.login('support1', 'Support@123').subscribe({ error: () => undefined });
    http.expectOne('/api/auth/login').flush({ ...response, expiresIn: 0 });
    expect(sessionStorage.length).toBe(0);
    expect(service.isAuthenticated()).toBeFalse();
  });
});

function seedSession(expiresAt: number): void {
  sessionStorage.setItem(TOKEN_KEY, 'test-token');
  sessionStorage.setItem(USER_KEY, JSON.stringify(response.user));
  sessionStorage.setItem(EXPIRY_KEY, JSON.stringify(expiresAt));
}
