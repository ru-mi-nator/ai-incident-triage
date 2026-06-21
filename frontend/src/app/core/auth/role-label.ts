import { UserRole } from './auth.models';

export function roleLabel(role: UserRole): string {
  return role === 'SUPPORT_ENGINEER' ? 'Support Engineer' : 'Developer';
}
