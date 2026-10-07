import { useCallback, useEffect, useRef } from "react";
import { useLocation, useNavigate, useNavigationType } from "react-router-dom";
import {
  flowKeyOf,
  planFinish,
  saveFlowStart,
  savePendingFinish,
  takePendingFinish,
} from "../lib/flowHistory";

/**
 * 앱에서 한 번 부른다 (App). 흐름 첫 화면에 새로 들어오면 방문 기록 위치를 남기고,
 * 흐름을 끝내며 되감아 도착하면 남겨 둔 도착 화면을 연다 (ADR 0047)
 */
export function useFlowHistoryScope(): void {
  const location = useLocation();
  const navigationType = useNavigationType();
  const navigate = useNavigate();
  // 이 문서에서 바로 전 화면의 흐름. null 이면 이 문서의 첫 화면
  const last = useRef<{ key: string | undefined } | null>(null);

  useEffect(() => {
    const pending = takePendingFinish();
    if (pending) {
      last.current = { key: undefined };
      navigate(pending.to, { replace: !pending.push, state: pending.state });
      return;
    }
    const key = flowKeyOf(location.pathname);
    // 다른 화면에서 새로 들어온 흐름만 남긴다. 뒤로가기 · 새로고침(POP)은 남기지 않는다
    if (key && key !== last.current?.key && navigationType !== "POP") {
      saveFlowStart(key, navigationType === "PUSH" && last.current !== null);
    }
    last.current = { key };
  }, [location.key, location.pathname, navigationType, navigate]);

  // 다른 사이트(카카오페이)에서 되감아 이 문서가 그대로 되살아났는데 주소 바뀜이 오지 않은 경우
  useEffect(() => {
    const onPageShow = (event: PageTransitionEvent) => {
      if (!event.persisted) return;
      window.setTimeout(() => {
        const pending = takePendingFinish();
        if (pending) navigate(pending.to, { replace: !pending.push, state: pending.state });
      }, 0);
    };
    window.addEventListener("pageshow", onPageShow);
    return () => window.removeEventListener("pageshow", onPageShow);
  }, [navigate]);
}

/**
 * 흐름을 끝내고 도착 화면을 연다. 흐름이 쌓은 방문 기록은 되감아, 뒤로가기로 흐름 화면에 돌아가지 못한다.
 * key 가 없거나 흐름 시작 기록이 없으면 지금 화면만 도착 화면으로 바꾼다
 */
export function useFinishFlow() {
  const navigate = useNavigate();

  return useCallback(
    (key: string | undefined, to: string, state?: unknown) => {
      const { back, push } = key ? planFinish(key) : { back: 0, push: false };
      if (back === 0 || !savePendingFinish({ to, state, push })) {
        navigate(to, { replace: true, state });
        return;
      }
      window.history.go(-back);
    },
    [navigate],
  );
}
