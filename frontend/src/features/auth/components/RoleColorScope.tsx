import { useLayoutEffect } from "react";
import { useLocation } from "react-router-dom";
import { colorRole } from "../lib/routeRole";

/**
 * 화면 주소의 역할을 <html data-role="owner | student"> 에 적는다.
 * 입력칸을 눌렀을 때 테두리처럼 역할 색이 필요한 곳은 CSS 의 --role-focus 로 고른다 (styles/tokens.css).
 * 그리는 것은 없다
 */
function RoleColorScope() {
  const { pathname } = useLocation();
  const role = colorRole(pathname);

  useLayoutEffect(() => {
    const root = document.documentElement;
    if (role) root.dataset.role = role;
    else delete root.dataset.role;
  }, [role]);

  return null;
}

export default RoleColorScope;
