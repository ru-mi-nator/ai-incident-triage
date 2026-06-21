import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiErrorResponse } from '../../core/auth/auth.models';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly hidePassword = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly isLoading = this.auth.isLoginPending;
  readonly loginForm = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required, Validators.pattern(/\S/)]],
    password: ['', Validators.required]
  });

  submit(): void {
    if (this.loginForm.invalid || this.isLoading()) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    const { username, password } = this.loginForm.getRawValue();
    this.auth.login(username.trim(), password).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        this.loginForm.reset();
        void this.router.navigateByUrl(this.safeReturnUrl(returnUrl));
      },
      error: (error: unknown) => {
        this.errorMessage.set(this.loginErrorMessage(error));
      }
    });
  }

  togglePasswordVisibility(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  private safeReturnUrl(returnUrl: string | null): string {
    return returnUrl?.startsWith('/') && !returnUrl.startsWith('//') && returnUrl !== '/login'
      ? returnUrl
      : '/dashboard';
  }

  private loginErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      const apiError = error.error as Partial<ApiErrorResponse> | null;
      if (apiError?.errorCode === 'INVALID_CREDENTIALS') {
        return 'The username or password is incorrect.';
      }
    }
    return 'Sign-in is unavailable right now. Please try again shortly.';
  }
}
