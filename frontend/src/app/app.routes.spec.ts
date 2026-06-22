import { authGuard } from './core/auth/auth.guard';
import { routes } from './app.routes';

describe('application routes', () => {
  it('protects the incident route through the authenticated shell', () => {
    const shellRoute = routes.find(route => route.path === '');
    const incidentRoute = shellRoute?.children?.find(route => route.path === 'incidents');

    expect(shellRoute?.canActivate).toContain(authGuard);
    expect(incidentRoute).toBeDefined();
    expect(incidentRoute?.loadComponent).toBeDefined();
  });
});
