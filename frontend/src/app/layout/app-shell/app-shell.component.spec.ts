import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AppShellComponent } from './app-shell.component';

describe('AppShellComponent', () => {
  afterEach(() => sessionStorage.clear());

  it('shows Dashboard, Incidents, and Create Incident links to support engineers', async () => {
    seedSession('SUPPORT_ENGINEER');
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [provideRouter([]), provideHttpClient()]
    }).compileComponents();
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
    const links = Array.from(
      fixture.nativeElement.querySelectorAll('.primary-nav a') as NodeListOf<HTMLAnchorElement>
    );

    expect(links.map(link => link.textContent?.trim())).toEqual([
      'dashboard Dashboard',
      'receipt_long Incidents',
      'add_circle Create Incident'
    ]);
    expect(links.map(link => link.getAttribute('href')))
      .toEqual(['/dashboard', '/incidents', '/incidents/new']);
  });

  it('hides Create Incident from developers', async () => {
    seedSession('DEVELOPER');
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [provideRouter([]), provideHttpClient()]
    }).compileComponents();
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Create Incident');
  });

  it('logs out, clears session, and returns to login', async () => {
    seedSession('SUPPORT_ENGINEER');
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([]),
        provideHttpClient()
      ]
    }).compileComponents();
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.componentInstance.logout();
    expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
    expect(sessionStorage.length).toBe(0);
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });
});

function seedSession(role: 'SUPPORT_ENGINEER' | 'DEVELOPER'): void {
  sessionStorage.setItem('incidentTriage.accessToken', 'test-token');
  sessionStorage.setItem('incidentTriage.user', JSON.stringify({
    id: 1,
    name: role === 'SUPPORT_ENGINEER' ? 'Support User 1' : 'Developer User 1',
    username: role === 'SUPPORT_ENGINEER' ? 'support1' : 'developer1',
    role
  }));
  sessionStorage.setItem('incidentTriage.expiresAt', JSON.stringify(Date.now() + 60_000));
}
