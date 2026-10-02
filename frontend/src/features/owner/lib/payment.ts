import type { PaymentMethod } from "../types";

export const PAYMENT_METHODS: { value: PaymentMethod; label: string; redirectTitle: string }[] = [
  { value: "kakaoPay", label: "카카오페이", redirectTitle: "카카오페이로 이동하고 있어요" },
  { value: "card", label: "신용·체크카드", redirectTitle: "카드 결제 창으로 이동하고 있어요" },
  { value: "transfer", label: "계좌이체", redirectTitle: "계좌이체 창으로 이동하고 있어요" },
];

/** 작업 중에 취소하면 학생에게 주는 착수 보상 비율 (노션 「취소·환불 정책」) */
export const START_REWARD_RATE = 0.2;

/** 작업 중 취소 때 학생 착수 보상 */
export function startReward(amount: number): number {
  return Math.round(amount * START_REWARD_RATE);
}
