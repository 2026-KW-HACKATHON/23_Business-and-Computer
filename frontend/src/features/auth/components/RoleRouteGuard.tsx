import { useSyncExternalStore } from "react";
import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { subscribeSession } from "../../../api/tokens";
import { getUserRole, landingPath } from "../lib/session";
import type { UserRole } from "../lib/session";

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

function requiredRole(pathname: string): UserRole | undefined {
  return ROLE_BY_PREFIX.find(
    ([prefix]) => pathname === prefix || pathname.startsWith(prefix.endsWith("/") ? prefix : `${prefix}/`),
  )?.[1];
}

/**
 * 화면 주소의 역할과 토큰의 역할이 다르면 화면을 그리기 전에 토큰에 맞는 첫 화면으로 보낸다.
 * 둘러보기 배지로 역할을 바꾼 뒤 「뒤로」를 눌러도 다른 역할의 화면이 열리지 않는다.
 * 토큰이 바뀌면(로그인 · 역할 전환 · 로그아웃) 다시 확인한다.
 */
function RoleRouteGuard({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const role = useSyncExternalStore(subscribeSession, getUserRole);
  const required = requiredRole(pathname);

  if (required && role !== required) return <Navigate to={landingPath()} replace />;
  return children;
}

export default RoleRouteGuard;
