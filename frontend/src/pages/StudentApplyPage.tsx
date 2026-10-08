import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Checkbox,
  Dialog,
  FormField,
  SubScreen,
  TextAreaField,
  TitleField,
  WorkKindIcon,
} from "../components";
import { landingPath } from "../features/auth";
import { JOB_APPLICATION_MAX_LENGTH, sendJobApplication, useJobDetail } from "../features/explore";
import { STUDENT_PATHS, StudentMissing } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";
import "./StudentApplyPage.css";
import { LoadNotice } from "../components";

type SendError = "duplicate" | "closed" | "invalidInput" | "retry";

const SEND_ERROR_TEXT: Record<SendError, string> = {
  duplicate: "이미 지원한 의뢰예요",
  closed: "모집이 끝난 의뢰예요",
  invalidInput: "입력한 내용을 다시 확인해 주세요",
  retry: "잠시 후 다시 시도해 주세요",
};

/**
 * 피그마 「지원하기」. GET /jobs/{id} 로 의뢰를 보여 주고, 지원서(한 줄 요약 · 작업계획서 · 결과물)를
 * 쓰고 마감 약속에 동의하면 POST /jobs/{id}/applications 로 보낸다 (ADR 0026).
 * 마감은 사장님이 의뢰에서 정했다. 보내면 「지원 완료 팝업창」.
 */
function StudentApplyPage() {
  const { requestId } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.explore);
  const { load, reload } = useJobDetail(requestId);
  const [summary, setSummary] = useState("");
  const [method, setMethod] = useState("");
  const [deliverable, setDeliverable] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<SendError | null>(null);
  const [missing, setMissing] = useState(false);
  const [sent, setSent] = useState(false);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 보낸다
  const inFlight = useRef(false);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestNumber = useRef(0);

  useEffect(() => {
    const latest = requestNumber;
    return () => {
      latest.current += 1;
    };
  }, []);

  if (load.status === "notFound" || missing) return <StudentMissing title="지원하기" onBack={back} />;

  if (load.status !== "loaded") {
    return (
      <SubScreen title="지원하기" onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="의뢰를 불러오는 중이에요"
          errorText="의뢰를 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }

  const { job } = load;
  if (job.status !== "OPEN") {
    return <StudentMissing title="지원하기" onBack={back} message="지원할 수 없는 의뢰예요" />;
  }

  // 이미 지원했거나 모집이 끝났으면 다시 보내지 않는다
  const alreadyApplied = job.applied != null;
  const blocked = alreadyApplied || sendError === "duplicate" || sendError === "closed";
  const shownError: SendError | null = alreadyApplied ? "duplicate" : sendError;
  const canSend =
    !blocked &&
    !sending &&
    !sent &&
    summary.trim() !== "" &&
    method.trim() !== "" &&
    deliverable.trim() !== "" &&
    agreed;

  const submit = async () => {
    const id = ++requestNumber.current;
    setSending(true);
    setSendError(null);
    const result = await sendJobApplication(job.id, { summary, method, deliverable });
    if (id !== requestNumber.current) return;
    setSending(false);

    switch (result.status) {
      case "sent":
        setSent(true);
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "notStudent":
        window.alert("학생만 의뢰에 지원할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "notFound":
        setMissing(true);
        break;
      case "duplicate":
      case "closed":
      case "invalidInput":
        setSendError(result.status);
        break;
      default:
        setSendError("retry");
    }
  };

  const send = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    try {
      await submit();
    } finally {
      inFlight.current = false;
    }
  };

  const terms = [`작업비 ${formatWon(job.budget)}`, `수정 ${job.revisionCount}회`];
  if (job.storeName) terms.unshift(job.storeName);

  return (
    <SubScreen
      title="지원하기"
      onBack={back}
      footer={
        <>
          {shownError && (
            <p className="student-apply__send-error" role="alert">
              {SEND_ERROR_TEXT[shownError]}
            </p>
          )}
          <Button
            loading={sending}
            loadingLabel="보내는 중"
            tone="student"
            fullWidth
            disabled={!canSend}
            onClick={() => void send()}
          >
            지원서 보내기
          </Button>
        </>
      }
    >
      <div className="student-detail">
        <div className="student-apply__request">
          <div className="student-detail__work-head">
            <WorkKindIcon kind="request" />
            <h2 className="student-detail__work-title">{job.title}</h2>
          </div>
          <p className="student-detail__work-meta">{terms.join(" · ")}</p>
          <p className="student-detail__work-meta">
            초안 마감 {formatMonthDay(job.draftDeadline)} · 최종 마감 {formatMonthDay(job.finalDeadline)}
          </p>
        </div>

        <div className="student-apply__intro">
          <h2 className="student-apply__title">지원서를 써 주세요</h2>
          <p className="student-apply__description">
            사장님은 전공·지원서·후기를 보고 학생을 골라요
          </p>
        </div>

        <FormField label="한 줄 요약" hint="카드에 가장 먼저 보여요" wrapsInput>
          <TitleField
            value={summary}
            maxLength={JOB_APPLICATION_MAX_LENGTH.summary}
            placeholder="예: 메뉴 32개를 번역하고 검수까지 받을게요"
            onChange={setSummary}
          />
        </FormField>

        <FormField label="작업계획서" hint="어떻게 만들고 검수할지 적어 주세요" wrapsInput>
          <TextAreaField
            value={method}
            maxLength={JOB_APPLICATION_MAX_LENGTH.method}
            placeholder="예: 메뉴 이름은 소리 나는 대로 적고, 아래에 재료와 맛을 한 줄로 설명해요"
            onChange={setMethod}
          />
        </FormField>

        <FormField label="결과물" hint="어떤 파일로 드릴지 적어 주세요" wrapsInput>
          <TextAreaField
            value={deliverable}
            maxLength={JOB_APPLICATION_MAX_LENGTH.deliverable}
            placeholder="예: 인쇄용 PDF와 바로 고칠 수 있는 원본 파일"
            onChange={setDeliverable}
          />
        </FormField>

        <Checkbox
          checked={agreed}
          onChange={setAgreed}
          label="선택되면 사장님이 정한 마감을 꼭 지킬게요 (필수)"
          description="연락이 끊기거나 마감을 넘기면 노쇼 페널티가 있어요"
        />
      </div>

      <Dialog
        open={sent}
        image="doneStudent"
        title="지원서를 보냈어요"
        description={"사장님이 작업계획서를 확인하면\n결과를 알림으로 알려 드려요."}
        actions={
          <Button
            tone="student"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.activity("applied"), { replace: true })}
          >
            지원 현황 보기
          </Button>
        }
      />
    </SubScreen>
  );
}

export default StudentApplyPage;
