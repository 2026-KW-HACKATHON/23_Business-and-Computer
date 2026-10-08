import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppImage, Button, FlowBar, LoadNotice, RoleAvatar, SubScreen } from "../components";
import { landingPath } from "../features/auth";
import {
  OWNER_PATHS,
  OwnerMissing,
  PaymentProgress,
  PaymentSection,
  flowSteps,
  parsePositiveId,
  proposalStudentRecord,
  studentMetaText,
  useJobAssignment,
} from "../features/owner";
import type { JobAssignment, PaymentPhase } from "../features/owner";
import { prepareJobPayment, startKakaoPay } from "../features/payment";
import type { PaymentFailure } from "../features/payment";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";
import "./OwnerPayPage.css";

/**
 * 피그마 「안전결제」. 고른 지원자에게 맡길 작업비를 골목인턴에 맡긴다.
 * 의뢰와 지원자는 「이 학생에게 맡기기」와 같이 불러온다 (useJobAssignment, ADR 0037).
 * 「안전결제하기」는 POST /jobs/{id}/payments 로 결제를 준비하고 카카오페이로 간다 (ADR 0031).
 * 카카오페이에서는 /payments/kakao/… (KakaoPayResultPage) 로 돌아온다.
 */
function OwnerPayPage() {
  const { requestId = "", applicationId = "" } = useParams();
  const back = useBack(OWNER_PATHS.assign(requestId, applicationId));
  const jobId = parsePositiveId(requestId);
  const jobApplicationId = parsePositiveId(applicationId);
  const { load, reload } = useJobAssignment(jobId, jobApplicationId);

  if (load.status === "notFound" || jobId === undefined || jobApplicationId === undefined) {
    return <OwnerMissing title="안전결제" onBack={back} />;
  }
  if (load.status === "closed") {
    return <OwnerMissing title="안전결제" onBack={back} message="모집이 끝나 결제할 수 없어요" />;
  }
  if (load.status !== "loaded") {
    return (
      <SubScreen title="안전결제" onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="결제할 의뢰를 불러오는 중이에요"
          errorText="결제할 의뢰를 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  return (
    <PayForm assignment={load.data} jobId={jobId} jobApplicationId={jobApplicationId} onBack={back} />
  );
}

/** 결제 준비가 실패했을 때: 알림을 띄우고 갈 곳. 없으면 결제 실패 팝업 */
function prepareFailureExit(reason: PaymentFailure, jobId: number): { message?: string; to: string } | undefined {
  const applicants = OWNER_PATHS.requestApplicants(String(jobId));
  switch (reason) {
    case "unauthorized":
      return { to: "/login" };
    case "notOwner":
      return { message: "사장님만 결제할 수 있어요", to: landingPath() };
    case "targetNotFound":
      return { message: "의뢰를 찾을 수 없어요", to: OWNER_PATHS.activity("sent") };
    case "applicationNotFound":
      return { message: "지원서를 찾을 수 없어요. 지원자 목록에서 다시 골라 주세요", to: applicants };
    case "orderNotPayable":
      return {
        message: "지금은 결제할 수 없어요. 모집이 끝났거나 이 학생의 지원이 대기 중이 아니에요",
        to: applicants,
      };
    case "alreadyPaid":
      return { message: "이미 결제된 의뢰예요", to: OWNER_PATHS.activity("inProgress") };
    default:
      return undefined;
  }
}

function PayForm({
  assignment,
  jobId,
  jobApplicationId,
  onBack,
}: {
  assignment: JobAssignment;
  jobId: number;
  jobApplicationId: number;
  onBack: () => void;
}) {
  const navigate = useNavigate();
  const [agreed, setAgreed] = useState(false);
  const [phase, setPhase] = useState<PaymentPhase>("idle");
  // 빠른 두 번 누름에도 결제 준비를 한 번만 보낸다
  const inFlight = useRef(false);
  const { job, applicant, revisionCount } = assignment;

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
    const result = await startKakaoPay({ kind: "job", jobId, jobApplicationId }, () =>
      prepareJobPayment(jobId, { jobApplicationId, refundPolicyAgreed: agreed }),
    );
    if (result.status === "redirect") {
      // 카카오페이 결제창으로 간다. 이동 화면은 페이지가 바뀔 때까지 그대로 둔다
      window.location.assign(result.url);
      return;
    }
    inFlight.current = false;
    const exit = prepareFailureExit(result.reason, jobId);
    if (!exit) {
      setPhase("failed");
      return;
    }
    if (exit.message) window.alert(exit.message);
    navigate(exit.to, { replace: true });
  };

  const meta = [studentMetaText(applicant.studentNumber, applicant.major), proposalStudentRecord(applicant)]
    .filter(Boolean)
    .join("\n");
  const terms = [
    `초안 ${formatMonthDay(job.draftDeadline)}`,
    `최종 ${formatMonthDay(job.finalDeadline)}`,
    ...(revisionCount !== undefined ? [`수정 ${revisionCount}회`] : []),
  ].join(" · ");

  return (
    <SubScreen
      title="안전결제"
      onBack={onBack}
      footer={
        <Button fullWidth disabled={!agreed || phase !== "idle"} onClick={() => void pay()}>
          {formatWon(job.budget)} 안전결제하기
        </Button>
      }
    >
      <div className="owner-pay">
        <FlowBar steps={flowSteps("의뢰", 1, "결제 후 시작")} />

        <section className="owner-pay__card">
          <div className="owner-pay__student">
            <RoleAvatar role="student" src={applicant.profileImageUrl} />
            <div className="owner-pay__student-info">
              <strong>{studentTitle(applicant.name)}에게 맡겨요</strong>
              <span>{meta}</span>
            </div>
          </div>
          <hr className="owner-pay__divider" />
          <div className="owner-pay__work">
            <AppImage name="iconCardRequest" width={20} />
            <span>{job.title}</span>
          </div>
          <p className="owner-pay__terms">{terms}</p>
        </section>

        <PaymentSection
          amount={job.budget}
          method="kakaoPay"
          methods={["kakaoPay"]}
          onMethodChange={() => undefined}
          agreed={agreed}
          onAgreeChange={setAgreed}
          showGuide
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

export default OwnerPayPage;
