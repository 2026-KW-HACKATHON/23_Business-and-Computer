import { useEffect, useRef, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  BudgetField,
  Button,
  CategoryBadge,
  FlowBar,
  FormField,
  LoadNotice,
  RevisionStepper,
  SubScreen,
  TextAreaField,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  PaymentProgress,
  PaymentSection,
  flowSteps,
} from "../features/owner";
import type { PaymentPhase } from "../features/owner";
import { landingPath } from "../features/auth";
import { prepareProposalPayment, startKakaoPay } from "../features/payment";
import type { PaymentFailure } from "../features/payment";
import { expectedDaysText, proposalBadgeNames, useProposalDetail } from "../features/proposal";
import type { ProposalDetail } from "../features/proposal";
import { useBack } from "../hooks/useBack";
import { addDays, formatMonthDayWeekday, todayIsoDate } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerPayPage.css";
import { studentTitle } from "../lib/korean";

/**
 * 피그마 「제안 수락 - 의뢰서작성·결제」. 학생 제안을 의뢰서로 바꾸면서 작업비 · 수정 횟수 ·
 * 학생에게 한마디를 정하고 바로 안전결제한다. 작업비는 학생 희망 작업비를 채워 두고 사장님이
 * 고칠 수 있다. 마감일은 결제한 날부터 학생이 제안한 기간이라 보여 주기만 한다 (서버가 정한다).
 * 제안은 GET /proposals/{id} 로 읽는다 (ADR 0025). 결정 대기(PENDING)가 아니면 상세로 돌려보낸다.
 * 「안전결제하기」는 POST /proposals/{id}/payments 로 결제를 준비하고 카카오페이로 간다 (ADR 0031).
 * 카카오페이에서는 /payments/kakao/… (KakaoPayResultPage) 로 돌아온다.
 */
function OwnerProposalAcceptPage() {
  const { proposalId = "" } = useParams();
  const back = useBack(OWNER_PATHS.proposal(proposalId));
  const { load, reload } = useProposalDetail(proposalId);

  if (load.status === "notFound") return <OwnerMissing title="제안 수락" onBack={back} />;
  if (load.status !== "loaded") {
    return (
      <SubScreen title="제안 수락" onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="제안을 불러오는 중이에요"
          errorText="제안을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  if (load.proposal.status !== "PENDING") {
    return <Navigate to={OWNER_PATHS.proposal(proposalId)} replace />;
  }
  // 작업비 기본값을 불러온 제안의 희망 금액으로 두려고, 불러온 뒤에 폼을 그린다
  return <AcceptForm key={load.proposal.proposalId} proposal={load.proposal} onBack={back} />;
}

/** 결제 준비가 실패했을 때: 알림을 띄우고 갈 곳. 없으면 결제 실패 팝업 */
function prepareFailureExit(
  reason: PaymentFailure,
  proposalId: number,
): { message?: string; to: string } | undefined {
  const detail = OWNER_PATHS.proposal(String(proposalId));
  switch (reason) {
    case "unauthorized":
      return { to: "/login" };
    case "otherStore":
      return { message: "우리 가게가 받은 제안만 결제할 수 있어요", to: OWNER_PATHS.activity("proposals") };
    case "targetNotPayable":
      return { message: "이미 결정한 제안이에요", to: detail };
    case "alreadyPaid":
      return { message: "이미 결제된 제안이에요", to: detail };
    case "notOwner":
      return { message: "사장님만 결제할 수 있어요", to: landingPath() };
    case "targetNotFound":
      return { message: "제안을 찾을 수 없어요", to: OWNER_PATHS.activity("proposals") };
    default:
      return undefined;
  }
}

function AcceptForm({ proposal, onBack }: { proposal: ProposalDetail; onBack: () => void }) {
  const navigate = useNavigate();
  // 학생 희망 작업비를 채워 두고 사장님이 고친다
  const [budget, setBudget] = useState(proposal.proposedFee);
  const [revisions, setRevisions] = useState(1);
  const [message, setMessage] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [phase, setPhase] = useState<PaymentPhase>("idle");
  // 빠른 두 번 누름에도 결제 준비를 한 번만 보낸다
  const inFlight = useRef(false);

  const canPay = agreed && budget > 0 && phase === "idle";

  // 카카오페이에서 브라우저 「뒤로」로 돌아와 이 화면이 그대로 되살아나면 이동 화면을 걷는다
  useEffect(() => {
    const onPageShow = (event: PageTransitionEvent) => {
      if (!event.persisted) return;
      inFlight.current = false;
      setPhase("idle");
    };
    window.addEventListener("pageshow", onPageShow);
    return () => window.removeEventListener("pageshow", onPageShow);
  }, []);

  const pay = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setPhase("redirecting");
    const { proposalId } = proposal;
    const result = await startKakaoPay({ kind: "proposal", proposalId }, () =>
      prepareProposalPayment(proposalId, {
        revisionCount: revisions,
        messageToStudent: message.trim(),
        budget,
        refundPolicyAgreed: agreed,
      }),
    );
    if (result.status === "redirect") {
      // 카카오페이 결제창으로 간다. 이동 화면은 페이지가 바뀔 때까지 그대로 둔다
      window.location.assign(result.url);
      return;
    }
    inFlight.current = false;
    const exit = prepareFailureExit(result.reason, proposalId);
    if (!exit) {
      setPhase("failed");
      return;
    }
    if (exit.message) window.alert(exit.message);
    navigate(exit.to, { replace: true });
  };

  return (
    <SubScreen
      title="제안 수락"
      onBack={onBack}
      footer={
        <Button fullWidth disabled={!canPay} onClick={() => void pay()}>
          안전결제하기
        </Button>
      }
    >
      <div className="owner-pay owner-pay--accept">
        <div className="owner-pay__intro">
          <h2 className="owner-pay__title">학생의 제안을 받아들일까요?</h2>
          <p className="owner-pay__description">
            {"제안을 바탕으로 의뢰서를 만들어요.\n희망 작업비를 보고 작업비와 수정 횟수를 정해 주세요."}
          </p>
        </div>

        <FlowBar steps={flowSteps("제안", 1, "결제 후 시작")} />

        <section className="owner-pay__summary">
          <div className="owner-pay__summary-head">
            <AppImage name="iconCardProposal" />
            <h3 className="owner-pay__summary-title">{proposal.title}</h3>
            {proposalBadgeNames(proposal.specialtyCategories).map((name) => (
              <CategoryBadge key={name} field={name} />
            ))}
          </div>
          <p className="owner-pay__terms">
            {studentTitle(proposal.student.name)} · 희망 작업비 {formatWon(proposal.proposedFee)} ·{" "}
            {expectedDaysText(proposal.draftDays, proposal.finalDays)}
          </p>
        </section>

        <FormField label="작업비" hint="학생 희망 작업비를 참고해 정해 주세요" wrapsInput>
          <BudgetField value={budget} onChange={setBudget} />
        </FormField>

        {/* 학생이 제안한 기간을 오늘(결제한 날)에 더한 실제 날짜로 보여 준다 */}
        <FormField label="마감일" hint="학생이 제안한 기간 · 오늘 결제하면">
          <p className="owner-pay__fixed">
            초안 {formatMonthDayWeekday(addDays(todayIsoDate(), proposal.draftDays))} · 최종{" "}
            {formatMonthDayWeekday(addDays(todayIsoDate(), proposal.finalDays))}
          </p>
        </FormField>

        <FormField label="수정 횟수" hint="최소 1회 · 등록한 뒤에는 바꿀 수 없어요">
          <RevisionStepper value={revisions} onChange={setRevisions} />
        </FormField>

        <FormField label="학생에게 한마디" hint="선택 · 작업할 때 참고할 점을 적어 주세요" wrapsInput>
          <TextAreaField
            value={message}
            maxLength={200}
            placeholder="예: 오후 3~5시는 한가해요. 그때 가게에 오시면 메뉴를 설명해 드릴게요"
            onChange={setMessage}
          />
        </FormField>

        <PaymentSection
          amount={budget}
          method="kakaoPay"
          methods={["kakaoPay"]}
          onMethodChange={() => undefined}
          agreed={agreed}
          onAgreeChange={setAgreed}
        />
      </div>

      {/* 이동 화면과 결제 실패 팝업만 쓴다. 결제 완료는 카카오페이에서 돌아온 화면이 띄운다 */}
      <PaymentProgress
        phase={phase}
        method="kakaoPay"
        onRetry={() => setPhase("idle")}
        onDone={() => setPhase("idle")}
      />
    </SubScreen>
  );
}

export default OwnerProposalAcceptPage;
