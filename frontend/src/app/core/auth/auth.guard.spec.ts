import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard, loginGuard } from './auth.guard';

describe('authentication guards', () => {
  let router: Router;
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([])]
    });
    router = TestBed.inject(Router);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => sessionStorage.clear());

  it('allows an authenticated session', () => {
    seedValidSession();
    auth.restoreSession();
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/dashboard' } as RouterStateSnapshot)
    );
    expect(result).toBeTrue();
  });

  it('redirects an unauthenticated user and preserves returnUrl', () => {
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/dashboard' } as RouterStateSnapshot)
    ) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/login?returnUrl=%2Fdashboard');
  });

  it('redirects an authenticated user away from login', () => {
    seedValidSession();
    auth.restoreSession();
    const result = TestBed.runInInjectionContext(() =>
      loginGuard({} as ActivatedRouteSnapshot, { url: '/login' } as RouterStateSnapshot)
    ) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/dashboard');
  });
});

function seedValidSession(): void {
  sessionStorage.setItem('incidentTriage.accessToken', 'test-token');
  sessionStorage.setItem('incidentTriage.user', JSON.stringify({
    id: 1, name: 'Support User 1', username: 'support1', role: 'SUPPORT_ENGINEER'
  }));
  sessionStorage.setItem('incidentTriage.expiresAt', JSON.stringify(Date.now() + 60_000));
}
