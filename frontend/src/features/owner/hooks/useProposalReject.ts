import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { landingPath } from "../../auth";
import { sendProposalReject } from "../lib/receivedProposals";

/** 받은 제안 거절 팝업 단계. confirm = 「제안을 거절할까요?」, done = 「학생의 제안을 거절했어요」 */
export type ProposalRejectStep = "closed" | "confirm" | "done";

export interface ProposalReject {
  step: ProposalRejectStep;
  /** 보내는 중 (거절하기 버튼에 점 세 개) */
  rejecting: boolean;
  /** 확인 팝업 설명 아래 실패 안내 */
  error: string | null;
  /** 「거절하기」를 눌러 그 제안의 확인 팝업을 연다 */
  ask: (proposalId: number) => void;
  /** 확인 팝업의 「거절하기」 */
  confirm: () => Promise<void>;
  /** 확인 팝업의 「돌아가기」 · 바깥. 보내는 중에는 닫히지 않는다 */
  close: () => void;
  /** 완료 팝업을 닫는다 */
  finish: () => void;
}

/**
 * 받은 제안 「거절하기」 (POST /proposals/{id}/reject, ADR 0033). 내 활동 받은 제안 카드와 받은 제안 상세가 쓴다.
 * 한 번 누르면 한 번만 보낸다. 성공하면 완료 팝업을 열고 onChange 로 화면을 다시 불러온다.
 * 결제하는 중이거나 이미 결제 · 취소돼 거절할 수 없으면 확인 팝업에 안내를 띄우고, 404 · 409 는 다시 불러온다.
 * 401 은 /login, 403 은 알림 뒤 landingPath().
 */
export function useProposalReject(onChange: () => void): ProposalReject {
  const navigate = useNavigate();
  const [target, setTarget] = useState<number | null>(null);
  const [step, setStep] = useState<ProposalRejectStep>("closed");
  const [rejecting, setRejecting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 보낸다
  const inFlight = useRef(false);

  const ask = (proposalId: number) => {
    setTarget(proposalId);
    setError(null);
    setStep("confirm");
  };

  const close = () => {
    if (!inFlight.current) setStep("closed");
  };

  const finish = () => setStep("closed");

  const confirm = async () => {
    if (target === null || inFlight.current) return;
    inFlight.current = true;
    setRejecting(true);
    setError(null);
    const result = await sendProposalReject(target);
    inFlight.current = false;
    setRejecting(false);
    switch (result.status) {
      case "rejected":
        setStep("done");
        onChange();
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "forbidden":
        window.alert("제안을 받은 사장님만 거절할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "paymentPending":
        setError("결제를 진행하는 중이라 지금은 거절할 수 없어요");
        break;
      case "notFound":
      case "notAvailable":
        setError("이미 결제했거나 끝난 제안이라 거절할 수 없어요");
        onChange();
        break;
      default:
        setError("잠시 후 다시 시도해 주세요");
    }
  };

  return { step, rejecting, error, ask, confirm, close, finish };
}
