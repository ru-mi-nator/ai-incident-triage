import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const LOGIN_URL = '/api/auth/login';
let redirectPending = false;

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isApiRequest = request.url === '/api' || request.url.startsWith('/api/');
  const isLoginRequest = request.url === LOGIN_URL;
  const token = auth.currentToken();
  const outgoing = isApiRequest && !isLoginRequest && token && auth.hasValidSession()
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && isApiRequest && !isLoginRequest) {
        auth.clearSession();
        if (!redirectPending && router.url !== '/login') {
          redirectPending = true;
          void Promise.resolve(router.navigate(['/login']))
            .finally(() => redirectPending = false);
        }
      }
      return throwError(() => error);
    })
  );
};
