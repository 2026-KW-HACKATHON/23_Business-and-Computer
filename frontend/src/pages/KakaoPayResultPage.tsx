import { useEffect, useRef } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { OWNER_PATHS, PaymentProgress } from "../features/owner";
import { clearPendingPayment, readPendingPayment, useKakaoPayApproval } from "../features/payment";
import type { PaymentFailure, PaymentTarget } from "../features/payment";
import { useFinishFlow } from "../hooks/useFlowHistory";
import { FLOW_KEYS } from "../lib/flowHistory";

/** 무엇을 결제했는지 모를 때(결제 대기 정보가 없거나 다른 주문) 가는 곳 */
const UNKNOWN_PAYMENT_PATH = OWNER_PATHS.activity("inProgress");

interface TargetScreens {
  /** 이미 결제된 주문을 다시 승인했을 때 알림 */
  alreadyPaid: string;
  /** 결제 완료 「확인」: 홈 */
  done: string;
  /** 결제를 마무리할 수 없을 때 상태를 볼 화면 */
  status: string;
  /** 끝낼 흐름 (맡기기 · 결제, 제안 수락). 결제가 끝나면 그 화면들을 방문 기록에서 지운다 */
  flow?: string;
  /** 「다시 결제하기」 */
  retry: string;
  /** 목록 */
  list: string;
  successDescription: string;
}

/** 결제 대상별 화면. 대상을 모르면 모두 사장님 내 활동 */
function targetScreens(target: PaymentTarget | undefined): TargetScreens {
  if (!target) {
    return {
      alreadyPaid: "이미 결제됐어요",
      done: OWNER_PATHS.home,
      status: UNKNOWN_PAYMENT_PATH,
      retry: UNKNOWN_PAYMENT_PATH,
      list: UNKNOWN_PAYMENT_PATH,
      successDescription: "작업비는 골목인턴이 보관해요.",
    };
  }
  if (target.kind === "proposal") {
    const id = String(target.proposalId);
    return {
      alreadyPaid: "이미 결제된 제안이에요",
      done: OWNER_PATHS.home,
      status: OWNER_PATHS.proposal(id),
      flow: FLOW_KEYS.proposalAccept(target.proposalId),
      retry: OWNER_PATHS.proposalAccept(id),
      list: OWNER_PATHS.activity("proposals"),
      successDescription: "작업비는 골목인턴이 보관해요.\n학생이 작업을 시작하면 알려 드릴게요",
    };
  }
  return {
    alreadyPaid: "이미 결제된 의뢰예요",
    done: OWNER_PATHS.home,
    status: OWNER_PATHS.activity("inProgress"),
    flow: FLOW_KEYS.ownerPay(target.jobId),
    retry: OWNER_PATHS.assignPay(String(target.jobId), String(target.jobApplicationId)),
    list: OWNER_PATHS.activity("inProgress"),
    successDescription: "작업비는 골목인턴이 보관해요.\n학생과 채팅으로 자세한 내용을 나눠 보세요.",
  };
}

/**
 * 승인 실패: 알림을 띄우고 갈 곳. undefined 면 결제 실패 팝업 (502 · 네트워크 · pg_token 없음).
 * finish 면 이미 결제가 끝난 것이라 결제 완료처럼 흐름을 끝낸다
 */
function approveFailureExit(
  reason: PaymentFailure,
  screens: TargetScreens,
): { message?: string; to: string; finish?: boolean } | undefined {
  switch (reason) {
    case "unauthorized":
      return { to: "/login" };
    case "alreadyPaid":
      return { message: screens.alreadyPaid, to: screens.done, finish: true };
    case "otherOwnerOrder":
      return { message: "다른 계정에서 진행한 결제예요. 결제한 사장님 계정으로 확인해 주세요", to: screens.list };
    case "orderNotFound":
      return { message: "결제 정보를 찾을 수 없어요. 다시 결제해 주세요", to: screens.retry };
    case "orderNotPayable":
      return { message: "결제를 마무리할 수 없는 상태예요. 내 활동에서 상태를 확인해 주세요", to: screens.status };
    case "notOwner":
      return { message: "사장님만 결제할 수 있어요", to: screens.list };
    default:
      return undefined;
  }
}

/** 성공 주소: 「결제를 확인하고 있어요」 → 승인 → 결제 완료 팝업. 승인 실패는 알림 뒤 이동 또는 결제 실패 팝업 */
function ApprovalResult({
  orderId,
  pgToken,
  target,
}: {
  orderId: string;
  pgToken: string | null;
  target: PaymentTarget | undefined;
}) {
  const navigate = useNavigate();
  const finishFlow = useFinishFlow();
  const approval = useKakaoPayApproval(orderId, pgToken);
  const screens = targetScreens(target);
  const flow = screens.flow;
  const exit = approval.status === "failed" ? approveFailureExit(approval.reason, screens) : undefined;
  const exitTo = exit?.to;
  const exitMessage = exit?.message;
  const exitFinishes = exit?.finish === true;
  // StrictMode 개발 모드에서 effect 가 두 번 돌아도 알림은 한 번만
  const exited = useRef(false);

  useEffect(() => {
    if (!exitTo || exited.current) return;
    exited.current = true;
    clearPendingPayment();
    if (exitMessage) window.alert(exitMessage);
    if (exitFinishes) finishFlow(flow, exitTo);
    else navigate(exitTo, { replace: true });
  }, [exitTo, exitMessage, exitFinishes, flow, finishFlow, navigate]);

  const leave = (to: string) => {
    clearPendingPayment();
    navigate(to, { replace: true });
  };

  // 결제 완료 「확인」: 홈으로. 맡기기 · 결제 (제안 수락) 화면은 방문 기록에서 지워 뒤로가기로 다시 결제하지 못한다
  const finish = () => {
    clearPendingPayment();
    finishFlow(flow, screens.done);
  };

  const phase =
    approval.status === "approving"
      ? "redirecting"
      : approval.status === "paid"
        ? "success"
        : exit
          ? "idle"
          : "failed";

  return (
    <PaymentProgress
      phase={phase}
      method="kakaoPay"
      redirectTitle="결제를 확인하고 있어요"
      redirectDescription="잠시만 기다려 주세요. 이 화면을 닫지 말아 주세요."
      successDescription={screens.successDescription}
      onDone={finish}
      onRetry={() => leave(screens.retry)}
    />
  );
}

/**
 * 카카오페이에서 돌아오는 화면 /payments/kakao/approval · cancel · fail (?orderId=…, 성공이면 &pg_token=…).
 * 카카오페이로 가기 전에 남긴 결제 대기 정보(sessionStorage)로 무엇을 결제했는지 안다 (ADR 0031).
 * 그 정보가 없거나 주문이 다르면: 성공 주소는 orderId · pg_token 으로 승인한 뒤 사장님 내 활동으로,
 * 취소 · 실패 주소는 바로 사장님 내 활동으로 보낸다. 취소 · 실패는 결제 실패 팝업 → 「다시 결제하기」.
 * 결제 대기 정보는 화면을 떠날 때 지워서, 승인 화면을 새로고침해도 같은 주문을 다시 승인한다.
 */
function KakaoPayResultPage() {
  const { outcome } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const orderId = params.get("orderId");
  const pending = readPendingPayment(orderId);

  const pgToken = params.get("pg_token");
  // 승인할 수 있으면 대상을 몰라도 승인한다 (결제한 돈이 승인 없이 남지 않게)
  if (outcome === "approval" && orderId && (pending || pgToken)) {
    return <ApprovalResult orderId={orderId} pgToken={pgToken} target={pending?.target} />;
  }
  if (!pending || (outcome !== "cancel" && outcome !== "fail")) {
    return <Navigate to={UNKNOWN_PAYMENT_PATH} replace />;
  }

  const screens = targetScreens(pending.target);
  return (
    <PaymentProgress
      phase="failed"
      method="kakaoPay"
      onDone={() => undefined}
      onRetry={() => {
        clearPendingPayment();
        navigate(screens.retry, { replace: true });
      }}
    />
  );
}

export default KakaoPayResultPage;
