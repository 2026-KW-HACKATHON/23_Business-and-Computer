import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  BudgetField,
  Button,
  CategoryBadge,
  DueDateFields,
  FlowBar,
  FormField,
  RevisionStepper,
  SubScreen,
  TextAreaField,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  PaymentProgress,
  PaymentSection,
  dueDatesReady,
  flowSteps,
  useOwnerProposal,
  useSafePayment,
} from "../features/owner";
import type { DueDates, PaymentMethod } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./OwnerPayPage.css";

/**
 * 피그마 「제안 수락 - 의뢰서작성·결제」. 학생 제안을 의뢰서로 바꾸면서
 * 작업비 · 마감일 · 수정 횟수를 정하고 바로 안전결제한다.
 */
function OwnerProposalAcceptPage() {
  const { proposalId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.proposal(proposalId));
  const proposal = useOwnerProposal(proposalId);
  const [budget, setBudget] = useState(proposal?.wishBudget ?? 0);
  const [dues, setDues] = useState<DueDates>({ draftDue: "", finalDue: "" });
  const [revisions, setRevisions] = useState(1);
  const [message, setMessage] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("kakaoPay");
  const [agreed, setAgreed] = useState(false);
  const payment = useSafePayment();

  if (!proposal) return <OwnerMissing title="제안 수락" onBack={back} />;

  const canPay = budget > 0 && dueDatesReady(dues) && agreed;

  return (
    <SubScreen
      title="제안 수락"
      onBack={back}
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
            {"제안을 바탕으로 의뢰서를 만들어요.\n작업비·마감일·수정 횟수를 정해 주세요."}
          </p>
        </div>

        <FlowBar steps={flowSteps("제안", 1, "결제 후 시작")} />

        <section className="owner-pay__summary">
          <div className="owner-pay__summary-head">
            <AppImage name="iconCardProposal" />
            <h3 className="owner-pay__summary-title">{proposal.title}</h3>
            <CategoryBadge field={proposal.field} />
          </div>
          <p className="owner-pay__terms">
            {proposal.student.name} 학생 · 희망 작업비 {formatWon(proposal.wishBudget)} · 예상{" "}
            {proposal.expectedDays}일
          </p>
        </section>

        <FormField label="작업비" hint="학생 희망 금액 · 바꿀 수 있어요" wrapsInput>
          <BudgetField value={budget} onChange={setBudget} />
        </FormField>

        <FormField label="마감일">
          <DueDateFields value={dues} onChange={setDues} />
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
