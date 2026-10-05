import { useRef, useSyncExternalStore } from "react";
import { useNavigate } from "react-router-dom";
import { getAccessToken, getDemoSessionId, subscribeSession } from "../../../api/tokens";
import type { Role } from "../../../types/role";
import { switchDemoRole } from "../lib/demo";
import { landingPath } from "../lib/session";

function isDemoSession(): boolean {
  return getDemoSessionId() !== null && getAccessToken() !== null;
}

/** 둘러보기(데모 로그인) 중인지. 로그인 · 역할 전환 · 로그아웃 때 다시 그린다 */
export function useIsDemo(): boolean {
  return useSyncExternalStore(subscribeSession, isDemoSession);
}

/**
 * 홈 배지에 넣을 역할 전환. 둘러보기 중이 아니면 undefined (배지를 그리지 않음).
 * 바뀌면 그 역할의 홈으로, 데모 쌍이 사라졌으면 로그인 화면으로 간다.
 */
export function useDemoRoleSwitch(current: Role): (() => void) | undefined {
  const navigate = useNavigate();
  const isDemo = useIsDemo();
  // 다시 그려지기 전에 두 번 눌러도 한 번만 바꾼다
  const switching = useRef(false);

  if (!isDemo) return undefined;

  return () => {
    if (switching.current) return;
    switching.current = true;
    void switchDemoRole(current === "owner" ? "student" : "owner").then((result) => {
      switching.current = false;
      if (result === "ok") {
        navigate(landingPath(), { replace: true });
      } else if (result === "expired") {
        window.alert("둘러보기 시간이 끝났어요. 처음부터 다시 둘러봐 주세요");
        navigate("/login", { replace: true });
      } else {
        window.alert("역할을 바꾸지 못했어요. 잠시 후 다시 시도해 주세요");
      }
    });
  };
}
