import { authGuard } from './core/auth/auth.guard';
import { supportEngineerGuard } from './core/auth/support-engineer.guard';
import { routes } from './app.routes';

describe('application routes', () => {
  it('protects the incident route through the authenticated shell', () => {
    const shellRoute = routes.find(route => route.path === '');
    const incidentRoute = shellRoute?.children?.find(route => route.path === 'incidents');

    expect(shellRoute?.canActivate).toContain(authGuard);
    expect(incidentRoute).toBeDefined();
    expect(incidentRoute?.loadComponent).toBeDefined();
  });

  it('protects create incident with authentication and the support role guard', () => {
    const shellRoute = routes.find(route => route.path === '');
    const createRoute = shellRoute?.children?.find(route => route.path === 'incidents/new');

    expect(shellRoute?.canActivate).toContain(authGuard);
    expect(createRoute?.canActivate).toContain(supportEngineerGuard);
    expect(createRoute?.loadComponent).toBeDefined();
  });
});
