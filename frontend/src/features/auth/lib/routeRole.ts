import type { UserRole } from "./session";

/**
 * 이 주소로 시작하는 화면은 그 역할만 연다 (/explore/… 는 사장님이 보는 다른 가게 글,
 * /payments/… 는 카카오페이에서 돌아오는 사장님 결제 화면)
 */
const ROLE_BY_PREFIX: [string, UserRole][] = [
  ["/owner", "owner"],
  ["/explore/", "owner"],
  ["/payments/", "owner"],
  ["/student", "student"],
];

/** 가입 화면은 아직 역할이 없는 사용자가 열지만, 어느 역할로 가입하는지는 주소로 안다 */
const SIGNUP_ROLE_BY_PREFIX: [string, UserRole][] = [
  ["/signup/owner", "owner"],
  ["/signup/student", "student"],
];

function roleOfPrefix(table: [string, UserRole][], pathname: string): UserRole | undefined {
  return table.find(
    ([prefix]) => pathname === prefix || pathname.startsWith(prefix.endsWith("/") ? prefix : `${prefix}/`),
  )?.[1];
}

/** 이 주소를 열 수 있는 역할. 누구나 여는 화면이면 undefined */
export function requiredRole(pathname: string): UserRole | undefined {
  return roleOfPrefix(ROLE_BY_PREFIX, pathname);
}

/** 이 주소의 화면을 칠할 역할 색 (사장님 · 학생). 역할이 없는 화면이면 undefined */
export function colorRole(pathname: string): UserRole | undefined {
  return requiredRole(pathname) ?? roleOfPrefix(SIGNUP_ROLE_BY_PREFIX, pathname);
}
