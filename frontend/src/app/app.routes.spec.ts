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

  it('protects and lazy loads incident details through the authenticated shell', () => {
    const shellRoute = routes.find(route => route.path === '');
    const detailsRoute = shellRoute?.children?.find(route => route.path === 'incidents/:id');

    expect(shellRoute?.canActivate).toContain(authGuard);
    expect(detailsRoute?.loadComponent).toBeDefined();
  });

  it('keeps the create route before the parameterized details route', () => {
    const children = routes.find(route => route.path === '')?.children ?? [];
    const createIndex = children.findIndex(route => route.path === 'incidents/new');
    const detailsIndex = children.findIndex(route => route.path === 'incidents/:id');

    expect(createIndex).toBeGreaterThanOrEqual(0);
    expect(detailsIndex).toBeGreaterThan(createIndex);
    expect(children[createIndex].canActivate).toContain(supportEngineerGuard);
  });
});
