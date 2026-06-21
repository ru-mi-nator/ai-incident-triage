import { Component, computed, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth/auth.service';
import { roleLabel } from '../../core/auth/role-label';

@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, MatIconModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  private readonly auth = inject(AuthService);
  readonly user = this.auth.currentUser;
  readonly role = computed(() => {
    const user = this.user();
    return user ? roleLabel(user.role) : '';
  });
}
