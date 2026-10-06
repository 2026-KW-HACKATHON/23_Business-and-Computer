import { ApiError } from "../../../api/client";
import { approvePayment } from "../api/paymentApi";
import type { PaymentPrepareResponse } from "../api/paymentApi";

/** 무엇을 결제하는지. 카카오페이에 다녀온 뒤 돌아갈 화면을 정한다 */
export type PaymentTarget = { kind: "proposal"; proposalId: number } | { kind: "job"; jobId: number };

/** 카카오페이로 가기 전에 sessionStorage 에 남기는 값 */
export interface PendingPayment {
  orderId: string;
  target: PaymentTarget;
}

const PENDING_PAYMENT_KEY = "gakkum.pendingPayment";

function isPaymentTarget(value: unknown): value is PaymentTarget {
  if (!value || typeof value !== "object") return false;
  const target = value as Record<string, unknown>;
  if (target.kind === "proposal") return Number.isSafeInteger(target.proposalId);
  if (target.kind === "job") return Number.isSafeInteger(target.jobId);
  return false;
}

/** 결제 대기 정보를 남긴다. 저장소를 못 쓰면 남기지 않는다 (돌아와서 내 활동으로 간다) */
export function savePendingPayment(pending: PendingPayment): void {
  try {
    sessionStorage.setItem(PENDING_PAYMENT_KEY, JSON.stringify(pending));
  } catch {
    // 개인정보 보호 모드 등에서 저장소를 못 쓴다
  }
}

/** 돌아온 주소의 orderId 와 같은 결제 대기 정보. 없거나 다른 주문이면 undefined */
export function readPendingPayment(orderId: string | null): PendingPayment | undefined {
  if (!orderId) return undefined;
  try {
    const raw = sessionStorage.getItem(PENDING_PAYMENT_KEY);
    const value = raw ? (JSON.parse(raw) as Partial<PendingPayment>) : undefined;
    return value?.orderId === orderId && isPaymentTarget(value.target)
      ? { orderId, target: value.target }
      : undefined;
  } catch {
    return undefined;
  }
}

export function clearPendingPayment(): void {
  try {
    sessionStorage.removeItem(PENDING_PAYMENT_KEY);
  } catch {
    // 저장소를 못 쓰면 지울 것도 없다
  }
}

/**
 * 모바일 기기인지. 모바일이면 카카오톡 앱으로 넘어가는 모바일 결제창, 아니면 QR 을 띄우는 PC 결제창을 쓴다.
 * Chromium 의 userAgentData.mobile, Android · iPhone · iPad · iPod · Mobile 이 들어간 user agent,
 * 그리고 Mac 으로 보이지만 터치가 되는 iPad(iPadOS 13+) 를 모바일로 본다.
 */
export function isMobileDevice(): boolean {
  const nav = navigator as Navigator & { userAgentData?: { mobile?: boolean } };
  if (nav.userAgentData?.mobile) return true;
  const ua = nav.userAgent;
  if (/Android|iPhone|iPad|iPod|Mobile/i.test(ua)) return true;
  return /Macintosh/.test(ua) && nav.maxTouchPoints > 1;
}

/** 결제 준비 응답에서 이 기기가 갈 카카오페이 결제창 주소 */
export function kakaoPayRedirectUrl(prepared: PaymentPrepareResponse): string {
  return isMobileDevice() ? prepared.nextRedirectMobileUrl : prepared.nextRedirectPcUrl;
}

/** 결제 준비 · 승인이 실패한 까닭 (오류 코드를 화면이 쓰는 값으로) */
export type PaymentFailure =
  /** 401 — apiData 가 /refresh 로 한 번 다시 시도한 뒤에도 401 */
  | "unauthorized"
  /** 403 PAYMENT_403_OWNER · OWNER_403 — 사장님(사장님 프로필)이 아님 */
  | "notOwner"
  /** 403 PROPOSAL_403_PAYMENT — 다른 가게가 받은 제안 */
  | "otherStore"
  /** 403 PAYMENT_403_FORBIDDEN — 이 사장님의 주문이 아님 */
  | "otherOwnerOrder"
  /** 404 PROPOSAL_404 · JOB_404 — 결제할 대상이 없음 */
  | "targetNotFound"
  /** 404 PAYMENT_404_ORDER — 주문이 없음 */
  | "orderNotFound"
  /** 409 PROPOSAL_409_PAYMENT — 결정 대기(PENDING)가 아닌 제안 */
  | "targetNotPayable"
  /** 409 PAYMENT_409_UNAVAILABLE — 승인할 수 없는 주문 · 대상 상태 */
  | "orderNotPayable"
  /** 409 PAYMENT_409_PAID — 이미 결제됨 */
  | "alreadyPaid"
  /** 5xx (PAYMENT_502_*) · 네트워크 · 그 밖의 400 */
  | "failed";

export function paymentFailureOf(error: unknown): PaymentFailure {
  if (!(error instanceof ApiError)) return "failed";
  if (error.status === 401) return "unauthorized";
  switch (error.code) {
    case "PAYMENT_403_OWNER":
    case "OWNER_403":
      return "notOwner";
    case "PROPOSAL_403_PAYMENT":
      return "otherStore";
    case "PAYMENT_403_FORBIDDEN":
      return "otherOwnerOrder";
    case "PROPOSAL_404":
    case "JOB_404":
      return "targetNotFound";
    case "PAYMENT_404_ORDER":
      return "orderNotFound";
    case "PROPOSAL_409_PAYMENT":
      return "targetNotPayable";
    case "PAYMENT_409_UNAVAILABLE":
      return "orderNotPayable";
    case "PAYMENT_409_PAID":
      return "alreadyPaid";
    default:
      return "failed";
  }
}

/** 결제 시작 결과. redirect 면 결제 대기 정보를 남겼고 url 로 가면 된다 */
export type PaymentStartResult = { status: "redirect"; url: string } | { status: "failed"; reason: PaymentFailure };

/**
 * 결제를 준비하고 카카오페이로 갈 주소를 돌려준다. prepare 는 대상별 결제 준비 요청
 * (제안: prepareProposalPayment, 의뢰: POST /jobs/{jobId}/payments)이다.
 */
export async function startKakaoPay(
  target: PaymentTarget,
  prepare: () => Promise<PaymentPrepareResponse>,
): Promise<PaymentStartResult> {
  try {
    const prepared = await prepare();
    savePendingPayment({ orderId: prepared.orderId, target });
    return { status: "redirect", url: kakaoPayRedirectUrl(prepared) };
  } catch (error) {
    return { status: "failed", reason: paymentFailureOf(error) };
  }
}

/** 승인 결과 */
export type PaymentApproveResult = { status: "paid" } | { status: "failed"; reason: PaymentFailure };

export async function confirmKakaoPay(orderId: string, pgToken: string): Promise<PaymentApproveResult> {
  try {
    await approvePayment(orderId, pgToken);
    return { status: "paid" };
  } catch (error) {
    return { status: "failed", reason: paymentFailureOf(error) };
  }
}
