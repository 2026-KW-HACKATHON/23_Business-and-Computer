import { useSyncExternalStore } from "react";
import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { subscribeSession } from "../../../api/tokens";
import { requiredRole } from "../lib/routeRole";
import { getUserRole, landingPath } from "../lib/session";

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
