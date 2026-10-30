import type { CurrentUser, Role } from "../types/api";

/** The landing page for a signed-in user's active role. */
export function dashboardPathFor(role: Role | undefined | null): string {
  switch (role) {
    case "ADMIN":
      return "/admin";
    case "TEACHER":
      return "/attendance";
    case "PARENT":
      return "/parent";
    default:
      return "/";
  }
}

/** Where a freshly authenticated user should land, honouring a forced password change. */
export function landingPathFor(user: CurrentUser | null): string {
  if (user?.mustChangePassword) {
    return "/change-password";
  }
  return dashboardPathFor(user?.activeRole);
}
