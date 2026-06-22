import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  it('links the View Incidents action to the incident list', async () => {
    sessionStorage.setItem('incidentTriage.accessToken', 'test-token');
    sessionStorage.setItem('incidentTriage.user', JSON.stringify({
      id: 1, name: 'Support User', username: 'support1', role: 'SUPPORT_ENGINEER'
    }));
    sessionStorage.setItem('incidentTriage.expiresAt', JSON.stringify(Date.now() + 60_000));
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideRouter([]), provideHttpClient()]
    }).compileComponents();

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    const action = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;

    expect(action.textContent).toContain('View Incidents');
    expect(action.getAttribute('href')).toBe('/incidents');
    sessionStorage.clear();
  });
});

