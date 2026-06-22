import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { supportEngineerGuard } from './support-engineer.guard';

describe('supportEngineerGuard', () => {
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

  it('allows support engineers', () => {
    seedSession('SUPPORT_ENGINEER');
    auth.restoreSession();

    const result = TestBed.runInInjectionContext(() =>
      supportEngineerGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    );

    expect(result).toBeTrue();
  });

  it('redirects developers safely to incidents', () => {
    seedSession('DEVELOPER');
    auth.restoreSession();

    const result = TestBed.runInInjectionContext(() =>
      supportEngineerGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    ) as UrlTree;

    expect(router.serializeUrl(result)).toBe('/incidents');
  });
});

function seedSession(role: 'SUPPORT_ENGINEER' | 'DEVELOPER'): void {
  sessionStorage.setItem('incidentTriage.accessToken', 'test-token');
  sessionStorage.setItem('incidentTriage.user', JSON.stringify({
    id: 1,
    name: role === 'SUPPORT_ENGINEER' ? 'Support User' : 'Developer User',
    username: role === 'SUPPORT_ENGINEER' ? 'support1' : 'developer1',
    role
  }));
  sessionStorage.setItem('incidentTriage.expiresAt', JSON.stringify(Date.now() + 60_000));
}

