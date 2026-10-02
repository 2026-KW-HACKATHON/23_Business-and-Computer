import { useEffect, useRef, useState } from "react";

/** idle = 결제 전, redirecting = 결제 창으로 가는 중, success / failed = 결과 팝업 */
export type PaymentPhase = "idle" | "redirecting" | "success" | "failed";

const REDIRECT_MS = 1500;

/**
 * 안전결제 진행 상태. 해커톤 버전은 실제 결제를 막아 두어서 1.5초 뒤 성공으로 본다.
 * 이동 화면을 누르면 결제 창을 닫은 것으로 보고 실패 팝업을 띄운다 (피그마 프로토타입과 같음).
 * 백엔드를 연동할 때 start 를 카카오페이 결제 준비 → 이동 → 승인 결과로 바꾼다.
 */
export function useSafePayment() {
  const [phase, setPhase] = useState<PaymentPhase>("idle");
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  const start = () => {
    setPhase("redirecting");
    timer.current = window.setTimeout(() => setPhase("success"), REDIRECT_MS);
  };

  const cancel = () => {
    window.clearTimeout(timer.current);
    setPhase("failed");
  };

  const reset = () => setPhase("idle");

  return { phase, start, cancel, reset };
}
