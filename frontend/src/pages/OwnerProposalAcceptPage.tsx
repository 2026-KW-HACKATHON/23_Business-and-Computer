import { useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
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
  useSafePayment,
} from "../features/owner";
import type { PaymentMethod } from "../features/owner";
import { expectedDaysText, proposalBadgeNames, useProposalDetail } from "../features/proposal";
import type { ProposalDetail } from "../features/proposal";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./OwnerPayPage.css";

/**
 * 피그마 「제안 수락 - 의뢰서작성·결제」. 학생 제안을 의뢰서로 바꾸면서 수정 횟수 · 학생에게
 * 한마디를 정하고 바로 안전결제한다. 작업비는 학생이 제안한 금액, 마감일은 결제한 날부터 학생이
 * 제안한 기간이라 보여 주기만 한다 (서버가 제안에서 정한다).
 * 제안은 GET /proposals/{id} 로 읽는다 (ADR 0025). 결정 대기(PENDING)가 아니면 상세로 돌려보낸다.
 * 결제 진행은 useSafePayment 다.
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

function AcceptForm({ proposal, onBack }: { proposal: ProposalDetail; onBack: () => void }) {
  const navigate = useNavigate();
  const [revisions, setRevisions] = useState(1);
  const [message, setMessage] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("kakaoPay");
  const [agreed, setAgreed] = useState(false);
  const payment = useSafePayment();

  const canPay = agreed;

  return (
    <SubScreen
      title="제안 수락"
      onBack={onBack}
      footer={
        <Button fullWidth disabled={!canPay} onClick={payment.start}>
          안전결제하기
        </Button>
      }
    >
      <div className="owner-pay owner-pay--accept">
        <div className="owner-pay__intro">
          <h2 className="owner-pay__title">학생의 제안을 받아들일까요?</h2>
          <p className="owner-pay__description">
            {"제안을 바탕으로 의뢰서를 만들어요.\n수정 횟수를 정해 주세요."}
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
            {proposal.student.name} 학생 · 희망 작업비 {formatWon(proposal.proposedFee)} · 예상{" "}
            {expectedDaysText(proposal.draftDays, proposal.finalDays)}
          </p>
        </section>

        <FormField label="작업비" hint="학생이 제안한 금액이에요">
          <p className="owner-pay__fixed">{formatWon(proposal.proposedFee)}</p>
        </FormField>

        <FormField label="마감일" hint="학생이 제안한 기간 · 결제한 날부터 세요">
          <p className="owner-pay__fixed">{expectedDaysText(proposal.draftDays, proposal.finalDays)}</p>
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
          amount={proposal.proposedFee}
          method={method}
          onMethodChange={setMethod}
          agreed={agreed}
          onAgreeChange={setAgreed}
        />
      </div>

      <PaymentProgress
        phase={payment.phase}
        method={method}
        onCancel={payment.cancel}
        onRetry={payment.reset}
        onDone={() => navigate(OWNER_PATHS.home, { replace: true })}
      />
    </SubScreen>
  );
}

export default OwnerProposalAcceptPage;
