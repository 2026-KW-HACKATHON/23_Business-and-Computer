import { apiData } from "../../../api/client";

/** 결제 준비 응답 (PaymentPrepareResponse). 의뢰 결제 · 제안 결제가 같은 모양이다 */
export interface PaymentPrepareResponse {
  orderId: string;
  amount: number;
  orderName: string;
  /** 카카오페이 결제창 (PC: QR 결제 화면) */
  nextRedirectPcUrl: string;
  /** 카카오페이 결제창 (모바일 웹: 카카오톡 앱으로 넘어간다) */
  nextRedirectMobileUrl: string;
}

/** 승인 응답 (PaymentApproveResponse) */
export interface PaymentApproveResponse {
  orderId: string;
  status: "PAID";
  amount: number;
  approvedAt: string;
  jobId: number;
  /** 의뢰 결제는 MATCHED, 제안 결제는 AWAITING_START */
  jobStatus: string;
}

/** POST /proposals/{id}/payments 요청 본문 (ProposalPaymentPrepareRequest) */
export interface ProposalPaymentRequest {
  /** 0 이상 (화면은 1 이상) */
  revisionCount: number;
  /** 5000자 이하. 비었으면 서버가 null 로 본다 */
  messageToStudent: string;
  /** true 여야 한다 */
  refundPolicyAgreed: boolean;
}

/** POST /proposals/{proposalId}/payments — 받은 제안의 결제를 준비한다. 금액은 서버가 제안 작업비로 정한다 */
export async function prepareProposalPayment(
  proposalId: number,
  request: ProposalPaymentRequest,
): Promise<PaymentPrepareResponse> {
  const data = await apiData<PaymentPrepareResponse | undefined>(`/proposals/${proposalId}/payments`, {
    method: "POST",
    body: JSON.stringify(request),
  });
  if (!data) throw new Error("Payment prepare response has no data");
  return data;
}

/** POST /payments/{orderId}/approve — 카카오페이에서 돌아온 pg_token 으로 승인한다. 이미 승인된 주문은 같은 결과를 준다 */
export async function approvePayment(orderId: string, pgToken: string): Promise<PaymentApproveResponse> {
  const data = await apiData<PaymentApproveResponse | undefined>(
    `/payments/${encodeURIComponent(orderId)}/approve`,
    { method: "POST", body: JSON.stringify({ pgToken }) },
  );
  if (!data) throw new Error("Payment approve response has no data");
  return data;
}
