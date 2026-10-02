import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppImage, Button, FlowBar, RoleAvatar, SubScreen } from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  PaymentProgress,
  PaymentSection,
  flowSteps,
  studentRecord,
  useOwnerCheckout,
  useSafePayment,
} from "../features/owner";
import type { PaymentMethod } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerPayPage.css";

/** 피그마 「안전결제」. 고른 학생에게 맡길 작업비를 가꿈에 맡긴다 */
function OwnerPayPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const checkout = useOwnerCheckout(workId);
  const [method, setMethod] = useState<PaymentMethod>("kakaoPay");
  const [agreed, setAgreed] = useState(false);
  const payment = useSafePayment();

  if (!checkout) return <OwnerMissing title="안전결제" onBack={back} />;
  const { request, applicant } = checkout;
  const { student } = applicant;

  return (
    <SubScreen
      title="안전결제"
      onBack={back}
      footer={
        <Button fullWidth disabled={!agreed} onClick={payment.start}>
          {formatWon(request.budget)} 안전결제하기
        </Button>
      }
    >
      <div className="owner-pay">
        <FlowBar steps={flowSteps("의뢰", 1, "결제 후 시작")} />

        <section className="owner-pay__card">
          <div className="owner-pay__student">
            <RoleAvatar role="student" />
            <div className="owner-pay__student-info">
              <strong>{student.name} 학생에게 맡겨요</strong>
              <span>{`${student.department} ${student.year}\n${studentRecord(student)}`}</span>
            </div>
          </div>
          <hr className="owner-pay__divider" />
          <div className="owner-pay__work">
            <AppImage name="iconCardRequest" width={20} />
            <span>{request.title}</span>
          </div>
          <p className="owner-pay__terms">
            초안 {formatMonthDay(request.draftDue)} · 최종 {formatMonthDay(request.finalDue)} · 수정{" "}
            {request.revisionLimit}회
          </p>
        </section>

        <PaymentSection
          amount={request.budget}
          method={method}
          onMethodChange={setMethod}
          agreed={agreed}
          onAgreeChange={setAgreed}
          showGuide
        />
      </div>

      <PaymentProgress
        phase={payment.phase}
        method={method}
        onCancel={payment.cancel}
        onRetry={payment.reset}
        onDone={() => navigate(OWNER_PATHS.activity("inProgress"), { replace: true })}
      />
    </SubScreen>
  );
}

export default OwnerPayPage;
