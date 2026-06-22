import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const supportEngineerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.currentRole() === 'SUPPORT_ENGINEER'
    ? true
    : router.createUrlTree(['/incidents']);
};

