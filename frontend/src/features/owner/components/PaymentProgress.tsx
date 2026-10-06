import { createPortal } from "react-dom";
import { Button, Dialog } from "../../../components";
import type { PaymentPhase } from "../hooks/useSafePayment";
import { PAYMENT_METHODS } from "../lib/payment";
import type { PaymentMethod } from "../types";
import "./PaymentProgress.css";

interface PaymentProgressProps {
  phase: PaymentPhase;
  method: PaymentMethod;
  /** 이동 화면 제목. 없으면 결제 수단의 「…로 이동하고 있어요」 */
  redirectTitle?: string;
  /** 이동 화면 설명 한 줄. 넣으면 기본 설명과 아래 「결제가 끝나면…」 줄 대신 이것만 보인다 */
  redirectDescription?: string;
  /** 결제 완료 팝업 설명. 없으면 의뢰 결제(useSafePayment) 문구 */
  successDescription?: string;
  /** 이동 화면을 누름 (결제 창을 닫음). 없으면 눌러도 그대로다 */
  onCancel?: () => void;
  /** 결제 완료 팝업 「확인」 */
  onDone: () => void;
  /** 결제 실패 팝업 「다시 결제하기」 */
  onRetry: () => void;
}

/** 피그마 「카카오페이 이동 중 (로딩)」 · 「결제 완료 팝업」 · 「결제 실패 팝업」 */
function PaymentProgress({
  phase,
  method,
  redirectTitle,
  redirectDescription,
  successDescription,
  onCancel,
  onDone,
  onRetry,
}: PaymentProgressProps) {
  if (phase === "redirecting") {
    const title = redirectTitle ?? PAYMENT_METHODS.find((m) => m.value === method)?.redirectTitle;
    return createPortal(
      <div className="pay-progress" role="status" aria-live="polite" onClick={onCancel}>
        <div className="pay-progress__body">
          <span className="pay-progress__spinner" aria-hidden="true" />
          <p className="pay-progress__title">{title}</p>
          <p className="pay-progress__description">
            {redirectDescription ??
              "결제 창이 열리면 결제를 마무리해 주세요.\n이 화면을 닫지 말고 잠시만 기다려 주세요."}
          </p>
        </div>
        {!redirectDescription && (
          <p className="pay-progress__note">결제가 끝나면 골목인턴으로 자동으로 돌아와요</p>
        )}
      </div>,
      document.body,
    );
  }

  return (
    <>
      <Dialog
        open={phase === "success"}
        image="doneOwner"
        title="결제가 완료되었어요."
        description={
          successDescription ??
          "작업비는 골목인턴이 보관해요.\n학생과 채팅으로 자세한 내용을 나눠 보세요.\n(구현을 완료했으나, 실제 결제는 막아두었습니다)"
        }
        actions={
          <Button fullWidth onClick={onDone}>
            확인
          </Button>
        }
      />
      <Dialog
        open={phase === "failed"}
        image="paymentFailOwner"
        title="결제에 실패했어요"
        description={"결제 실패로 작업이 확정되지 않았어요.\n결제 수단을 확인하고 다시 시도해 주세요."}
        actions={
          <Button fullWidth onClick={onRetry}>
            다시 결제하기
          </Button>
        }
      />
    </>
  );
}

export default PaymentProgress;
