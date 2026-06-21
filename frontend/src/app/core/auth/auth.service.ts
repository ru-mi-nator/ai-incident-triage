import { HttpClient } from '@angular/common/http';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { finalize, Observable, tap } from 'rxjs';
import { AuthenticatedUser, LoginRequest, LoginResponse, UserRole } from './auth.models';

const TOKEN_KEY = 'incidentTriage.accessToken';
const USER_KEY = 'incidentTriage.user';
const EXPIRY_KEY = 'incidentTriage.expiresAt';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly userState = signal<AuthenticatedUser | null>(null);
  private readonly tokenState = signal<string | null>(null);
  private readonly expiresAtState = signal<number | null>(null);
  private readonly loginPendingState = signal(false);
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;

  readonly currentUser = this.userState.asReadonly();
  readonly currentToken = this.tokenState.asReadonly();
  readonly isLoginPending = this.loginPendingState.asReadonly();
  readonly isAuthenticated = computed(() => Boolean(
    this.tokenState() && this.userState() && this.expiresAtState()
  ));
  readonly currentRole = computed<UserRole | null>(() => this.currentUser()?.role ?? null);

  constructor() {
    this.restoreSession();
    this.destroyRef.onDestroy(() => this.cancelExpiryTimer());
  }

  login(username: string, password: string): Observable<LoginResponse> {
    if (this.loginPendingState()) {
      throw new Error('A login request is already in progress.');
    }

    this.loginPendingState.set(true);
    const request: LoginRequest = { username, password };
    return this.http.post<LoginResponse>('/api/auth/login', request).pipe(
      tap((response) => this.saveSession(response)),
      finalize(() => this.loginPendingState.set(false))
    );
  }

  logout(): void {
    this.clearSession();
  }

  hasValidSession(): boolean {
    const token = this.tokenState();
    const user = this.userState();
    const expiresAt = this.expiresAtState();
    const valid = Boolean(token && user && expiresAt && expiresAt > Date.now());

    if (!valid && (token || user || expiresAt)) {
      this.clearSession();
    }
    return valid;
  }

  restoreSession(): void {
    try {
      const token = sessionStorage.getItem(TOKEN_KEY);
      const userJson = sessionStorage.getItem(USER_KEY);
      const expiryJson = sessionStorage.getItem(EXPIRY_KEY);

      if (!token && !userJson && !expiryJson) {
        return;
      }
      if (!token || !userJson || !expiryJson) {
        this.clearSession();
        return;
      }

      const user: unknown = JSON.parse(userJson);
      const expiresAt: unknown = JSON.parse(expiryJson);
      if (!this.isAuthenticatedUser(user)
        || typeof expiresAt !== 'number'
        || !Number.isFinite(expiresAt)
        || expiresAt <= Date.now()) {
        this.clearSession();
        return;
      }

      const safeUser: AuthenticatedUser = {
        id: user.id,
        name: user.name,
        username: user.username,
        role: user.role
      };
      this.tokenState.set(token);
      this.userState.set(safeUser);
      this.expiresAtState.set(expiresAt);
      this.scheduleExpiry(expiresAt);
    } catch {
      this.clearSession();
    }
  }

  clearSession(): void {
    this.cancelExpiryTimer();
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(EXPIRY_KEY);
    this.tokenState.set(null);
    this.userState.set(null);
    this.expiresAtState.set(null);
  }

  private saveSession(response: LoginResponse): void {
    if (!this.isLoginResponse(response)) {
      throw new Error('The login response is invalid.');
    }

    const expiresAt = Date.now() + response.expiresIn * 1000;
    const user: AuthenticatedUser = {
      id: response.user.id,
      name: response.user.name,
      username: response.user.username,
      role: response.user.role
    };

    try {
      sessionStorage.setItem(TOKEN_KEY, response.accessToken);
      sessionStorage.setItem(USER_KEY, JSON.stringify(user));
      sessionStorage.setItem(EXPIRY_KEY, JSON.stringify(expiresAt));
      this.tokenState.set(response.accessToken);
      this.userState.set(user);
      this.expiresAtState.set(expiresAt);
      this.scheduleExpiry(expiresAt);
    } catch (error: unknown) {
      this.clearSession();
      throw error;
    }
  }

  private isAuthenticatedUser(value: unknown): value is AuthenticatedUser {
    if (typeof value !== 'object' || value === null) {
      return false;
    }
    const candidate = value as Record<string, unknown>;
    return typeof candidate['id'] === 'number'
      && Number.isFinite(candidate['id'])
      && candidate['id'] > 0
      && typeof candidate['name'] === 'string'
      && candidate['name'].length > 0
      && typeof candidate['username'] === 'string'
      && candidate['username'].length > 0
      && (candidate['role'] === 'SUPPORT_ENGINEER' || candidate['role'] === 'DEVELOPER');
  }

  private isLoginResponse(value: unknown): value is LoginResponse {
    if (typeof value !== 'object' || value === null) {
      return false;
    }
    const candidate = value as Record<string, unknown>;
    return typeof candidate['accessToken'] === 'string'
      && candidate['accessToken'].length > 0
      && candidate['tokenType'] === 'Bearer'
      && typeof candidate['expiresIn'] === 'number'
      && Number.isFinite(candidate['expiresIn'])
      && candidate['expiresIn'] > 0
      && this.isAuthenticatedUser(candidate['user']);
  }

  private scheduleExpiry(expiresAt: number): void {
    this.cancelExpiryTimer();
    this.expiryTimer = setTimeout(
      () => this.clearSession(),
      Math.max(0, expiresAt - Date.now())
    );
  }

  private cancelExpiryTimer(): void {
    if (this.expiryTimer !== undefined) {
      clearTimeout(this.expiryTimer);
      this.expiryTimer = undefined;
    }
  }
}
