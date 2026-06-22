export type UserRole = 'SUPPORT_ENGINEER' | 'DEVELOPER';

export interface AuthenticatedUser {
  readonly id: number;
  readonly name: string;
  readonly username: string;
  readonly role: UserRole;
}

export interface LoginRequest {
  readonly username: string;
  readonly password: string;
}

export interface LoginResponse {
  readonly accessToken: string;
  readonly tokenType: string;
  readonly expiresIn: number;
  readonly user: AuthenticatedUser;
}

export interface ApiErrorResponse {
  readonly timestamp: string;
  readonly status: number;
  readonly errorCode: string;
  readonly message: string;
  readonly path: string;
  readonly fieldErrors?: Readonly<Record<string, string>>;
}
