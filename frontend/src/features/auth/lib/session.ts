import { clearTokens, getAccessToken } from "./tokenStorage";

/** Account state read from the access token's `role` claim. */
export type UserRole = "owner" | "student" | "pending";

const ROLE_BY_CLAIM: Record<string, UserRole> = {
  OWNER: "owner",
  STUDENT: "student",
  PENDING: "pending",
};

/** Decodes one base64url JWT segment into a UTF-8 string. */
function decodeSegment(segment: string): string {
  const base64 = segment.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
  const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/**
 * Role of the signed-in user, or null when there is no readable token.
 * PENDING means the social login succeeded but signup is not finished.
 * Tokens from the social login can carry the prefix twice
 * (`ROLE_ROLE_PENDING`), so every leading `ROLE_` is stripped.
 */
export function getUserRole(): UserRole | null {
  const token = getAccessToken();
  if (!token) return null;
  try {
    const payload = JSON.parse(decodeSegment(token.split(".")[1] ?? "")) as { role?: unknown };
    if (typeof payload.role !== "string") return null;
    return ROLE_BY_CLAIM[payload.role.replace(/^(ROLE_)+/, "")] ?? null;
  } catch {
    return null;
  }
}

/**
 * First screen after the intro or a social login (Notion 「화면 상태 전환표」):
 * no token → /login, signup not finished → /signup/role, owner → /owner.
 * Students land on the temporary /home until the student home exists.
 * A token whose role cannot be read is dropped and the user logs in again;
 * clearing is idempotent, so calling this during render stays safe.
 */
export function landingPath(): string {
  if (!getAccessToken()) return "/login";
  switch (getUserRole()) {
    case "owner":
      return "/owner";
    case "pending":
      return "/signup/role";
    case "student":
      return "/home";
    default:
      clearTokens();
      return "/login";
  }
}
