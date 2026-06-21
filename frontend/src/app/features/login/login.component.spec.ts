import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('prevents blank submission and displays required validation', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.submit();
    fixture.detectChanges();
    expect(fixture.componentInstance.loginForm.invalid).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('mat-error').length).toBe(2);
    http.expectNone('/api/auth/login');
  });

  it('prevents a whitespace-only username from submitting', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.loginForm.setValue({ username: '   ', password: 'Support@123' });
    fixture.componentInstance.submit();
    expect(fixture.componentInstance.loginForm.invalid).toBeTrue();
    http.expectNone('/api/auth/login');
  });

  it('shows a safe invalid-credentials message', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.loginForm.setValue({ username: 'support1', password: 'wrong' });
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/login').flush(
      { errorCode: 'INVALID_CREDENTIALS' },
      { status: 401, statusText: 'Unauthorized' }
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent)
      .toContain('The username or password is incorrect.');
  });

  it('navigates to the dashboard after successful login', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.loginForm.setValue({ username: 'support1', password: 'Support@123' });
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/login').flush({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: { id: 1, name: 'Support User 1', username: 'support1', role: 'SUPPORT_ENGINEER' }
    });
    expect(navigateSpy).toHaveBeenCalledWith('/dashboard');
    expect(fixture.componentInstance.loginForm.controls.password.value).toBe('');
  });

  it('shows a generic safe message for backend failures', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.loginForm.setValue({ username: 'support1', password: 'Support@123' });
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/login').flush(
      { message: 'Sensitive backend exception details' },
      { status: 503, statusText: 'Unavailable' }
    );
    fixture.detectChanges();
    const message = fixture.nativeElement.querySelector('[role="alert"]').textContent;
    expect(message).toContain('Sign-in is unavailable right now.');
    expect(message).not.toContain('Sensitive backend exception details');
  });

  it('rejects an external returnUrl after successful login', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ returnUrl: '//evil.example' }) } }
        }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.componentInstance.loginForm.setValue({ username: 'support1', password: 'Support@123' });
    fixture.componentInstance.submit();
    http.expectOne('/api/auth/login').flush({
      accessToken: 'token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: { id: 1, name: 'Support User 1', username: 'support1', role: 'SUPPORT_ENGINEER' }
    });
    expect(navigateSpy).toHaveBeenCalledWith('/dashboard');
  });
});
