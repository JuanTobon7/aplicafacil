import { api } from "./http";

export type User = {
  id: string;
  firstName: string | null;
  lastName: string | null;
  headline: string | null;
  photoUrl: string | null;
  publicProfileUrl: string | null;
  location: string | null;
  email: string | null;
  emailVerified: boolean;
  lastLoginAt: string | null;
};

export type Session = { accessToken: string; expiresAt: string; user: User };

export type Account = {
  userId: string;
  displayName: string;
  headline: string | null;
  photoUrl: string | null;
  connectionStatus: "DISCONNECTED" | "CONNECTING" | "CONNECTED" | "EXPIRED" | "UNKNOWN";
};

export const authApi = {
  config: () => api<{ oauthEnabled: boolean }>("GET", "/auth/config"),
  accounts: () => api<Account[]>("GET", "/auth/accounts"),
  /** Canjea el código de un solo uso que llega por SSE al terminar la conexión. */
  exchange: (attemptId: string, loginCode: string) =>
    api<Session>("POST", "/auth/session", { attemptId, loginCode }),
  continueAs: (userId: string) => api<Session>("POST", `/auth/accounts/${userId}/session`),
  refresh: () => api<Session>("POST", "/auth/refresh", undefined, false),
  logout: () => api<void>("POST", "/auth/logout"),
  me: () => api<User>("GET", "/auth/me"),
  deleteMe: () => api<void>("DELETE", "/auth/me"),
};

export function displayName(user: Pick<User, "firstName" | "lastName">): string {
  return [user.firstName, user.lastName].filter(Boolean).join(" ") || "Cuenta de LinkedIn";
}
