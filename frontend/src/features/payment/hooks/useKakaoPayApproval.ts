import { useEffect, useState } from "react";
import { confirmKakaoPay } from "../lib/kakaoPay";
import type { PaymentFailure } from "../lib/kakaoPay";

export type KakaoPayApproval =
  | { status: "approving" }
  | { status: "paid" }
  | { status: "failed"; reason: PaymentFailure };

/**
 * 카카오페이 성공 주소로 돌아온 화면의 승인 (POST /payments/{orderId}/approve).
 * pg_token 이 없으면 요청하지 않고 실패로 본다. 승인은 같은 주문을 다시 보내도 같은 결과라
 * 새로고침 · StrictMode 의 두 번 실행에도 안전하고, 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useKakaoPayApproval(orderId: string, pgToken: string | null): KakaoPayApproval {
  const [result, setResult] = useState<{ key: string; approval: KakaoPayApproval }>();
  const key = `${orderId}:${pgToken}`;

  useEffect(() => {
    if (!pgToken) return;
    let active = true;
    void confirmKakaoPay(orderId, pgToken).then((approved) => {
      if (active) setResult({ key, approval: approved });
    });
    return () => {
      active = false;
    };
  }, [orderId, pgToken, key]);

  if (!pgToken) return { status: "failed", reason: "failed" };
  return result?.key === key ? result.approval : { status: "approving" };
}
