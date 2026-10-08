import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Checkbox,
  Dialog,
  FormField,
  LoadNotice,
  ReportSheet,
  SubScreen,
  TextAreaField,
  TextButton,
  WorkKindIcon,
} from "../components";
import { landingPath } from "../features/auth";
import {
  OWNER_PATHS,
  OwnerMissing,
  RefundBreakdown,
  parsePositiveId,
  sendJobCancel,
  startReward,
  useOwnerProgressJobs,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";
import type { WorkKind } from "../types/workKind";
import "./OwnerWorkCancelPage.css";

/**
 * 피그마 「작업 취소 - 이유·환불 금액」. 학생이 작업을 시작했으면 착수 보상 20%를 뺀
 * 금액을 돌려받고, 결과물을 받은 뒤에는 취소할 수 없다 (노션 「취소·환불 정책」).
 * 취소 이유와 학생에게 남길 말은 둘 다 적어야 한다 (POST /jobs/{id}/cancel).
 * 서버 작업(ADR 0035). 주소의 id 가 숫자가 아니면 찾을 수 없음.
 */
function OwnerWorkCancelPage() {
  const { workId = "" } = useParams();
  const back = useBack(OWNER_PATHS.home);
  const jobId = parsePositiveId(workId);
  return jobId !== undefined ? <JobCancel jobId={jobId} /> : <OwnerMissing title="작업 취소" onBack={back} />;
}

/** 취소 화면 본문. 취소할 수 있을 때만 이유 · 남길 말 · 환불 금액 · 확인 체크를 보인다 */
function CancelBody({
  kind,
  title,
  meta,
  cancelable,
  budget,
  reason,
  onReason,
  message,
  onMessage,
  agreed,
  onAgreed,
  onReport,
}: {
  kind: WorkKind;
  title: string;
  meta: ReactNode;
  cancelable: boolean;
  budget: number;
  reason: string;
  onReason: (reason: string) => void;
  message: string;
  onMessage: (message: string) => void;
  agreed: boolean;
  onAgreed: (agreed: boolean) => void;
  onReport: () => void;
}) {
  return (
    <div className="owner-cancel">
      <section className="owner-cancel__work">
        <div className="owner-cancel__work-head">
          <WorkKindIcon kind={kind} size={22} />
          <h2 className="owner-cancel__work-title">{title}</h2>
        </div>
        <p className="owner-cancel__meta">{meta}</p>
      </section>

      <div className="owner-cancel__intro">
        <h2 className="owner-cancel__title">작업을 취소할까요?</h2>
        <p className="owner-cancel__description">
          {cancelable
            ? "학생이 이미 작업을 시작해서, 착수 보상 20%를 뺀 금액을 돌려받아요."
            : "결과물을 받은 뒤에는 취소할 수 없어요. 수정 요청이나 완료 확인만 할 수 있어요."}
        </p>
      </div>

      {cancelable && (
        <>
          <FormField label="취소 이유" wrapsInput>
            <TextAreaField
              value={reason}
              maxLength={300}
              placeholder="예: 작업이 필요없어졌어요."
              onChange={onReason}
            />
          </FormField>

          <FormField label="학생에게 남길 말" wrapsInput>
            <TextAreaField
              value={message}
              maxLength={300}
              placeholder="예: 가게 사정으로 미루게 됐어요. 죄송해요."
              onChange={onMessage}
            />
          </FormField>

          <RefundBreakdown
            title="돌려받는 금액"
            amount={budget}
            reward={startReward(budget)}
            note="학생이 아직 시작하지 않았으면 전액을 돌려받아요. 결과물을 받은 뒤에는 취소할 수 없어요."
          />
        </>
      )}

      <div className="owner-cancel__report">
        <span>학생이 연락이 안 되거나 약속을 안 지켰나요?</span>
        <TextButton onClick={onReport}>학생 문제 신고</TextButton>
      </div>

      {cancelable && (
        <Checkbox checked={agreed} onChange={onAgreed} label="취소 후에는 되돌릴 수 없다는 걸 확인했어요" />
      )}
    </div>
  );
}

type CancelError = "notAvailable" | "invalidInput" | "retry";

const CANCEL_ERROR_TEXT: Record<CancelError, string> = {
  notAvailable: "지금은 취소할 수 없는 작업이에요. 내 활동에서 상태를 확인해 주세요",
  invalidInput: "입력한 내용을 다시 확인해 주세요",
  retry: "잠시 후 다시 시도해 주세요",
};

/**
 * 서버 작업의 작업 취소 (ADR 0035). 작업 · 학생 이름 · 작업비는 진행 중 목록(GET /me/jobs?status=MATCHED,
 * 채팅방)에서 불러온다. 결과물이 도착해 있으면 취소할 수 없다고 안내한다. 취소한 뒤 팝업 금액은 서버 답을 쓴다.
 */
function JobCancel({ jobId }: { jobId: number }) {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.activity("inProgress"));
  const { load, reload } = useOwnerProgressJobs();
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [sending, setSending] = useState(false);
  const [canceled, setCanceled] = useState<{ refund: number; reward: number }>();
  const [cancelError, setCancelError] = useState<CancelError | null>(null);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 취소한다
  const inFlight = useRef(false);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestRef = useRef(0);

  useEffect(() => {
    const latest = requestRef;
    return () => {
      latest.current += 1;
    };
  }, []);

  const job = load.status === "loaded" ? load.jobs.find((j) => j.jobId === jobId) : undefined;
  if (load.status === "loaded" && !job) {
    return <OwnerMissing title="작업 취소" onBack={back} message="진행 중인 작업이 아니에요" />;
  }
  // 작업비를 모르면(채팅방을 못 불러옴) 환불 금액을 보일 수 없어 다시 불러온다
  if (!job || job.budget === undefined) {
    return (
      <SubScreen title="작업 취소" onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status === "loading" ? "loading" : "error"}
          loadingText="작업을 불러오는 중이에요"
          errorText="작업을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }

  const budget = job.budget;
  const cancelable = job.stage !== "submitted";
  const who = studentTitle(job.student.name ?? "학생");
  const stage =
    job.stage === "revising" ? "수정안 제작 중" : job.stage === "drafting" ? "초안 제작 중" : "결과물 도착";
  const ready = cancelable && reason.trim() !== "" && message.trim() !== "" && agreed;

  const cancel = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    const id = ++requestRef.current;
    setSending(true);
    setCancelError(null);
    const result = await sendJobCancel(jobId, reason, message);
    inFlight.current = false;
    if (id !== requestRef.current) return;
    setSending(false);
    switch (result.status) {
      case "canceled": {
        const reward = result.studentCompensationAmount ?? startReward(budget);
        setCanceled({ refund: result.refundAmount ?? budget - reward, reward });
        break;
      }
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "forbidden":
        window.alert("내 의뢰만 취소할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "notFound":
      case "notAvailable":
        setCancelError("notAvailable");
        break;
      case "invalidInput":
        setCancelError("invalidInput");
        break;
      default:
        setCancelError("retry");
    }
  };

  return (
    <SubScreen
      title="작업 취소"
      onBack={back}
      footer={
        <>
          {cancelError && (
            <p className="owner-cancel__send-error" role="alert">
              {CANCEL_ERROR_TEXT[cancelError]}
            </p>
          )}
          <Button fullWidth disabled={!ready || sending || canceled !== undefined} onClick={() => void cancel()}>
            {sending ? "취소하는 중..." : "작업 취소하기"}
          </Button>
        </>
      }
    >
      <CancelBody
        kind={job.kind}
        title={job.title}
        meta={`${who} · ${stage} · 작업비 ${formatWon(budget)}`}
        cancelable={cancelable}
        budget={budget}
        reason={reason}
        onReason={setReason}
        message={message}
        onMessage={setMessage}
        agreed={agreed}
        onAgreed={setAgreed}
        onReport={() => setReportOpen(true)}
      />

      <ReportSheet open={reportOpen} workTitle={job.title} onClose={() => setReportOpen(false)} />

      <Dialog
        open={canceled !== undefined}
        image="doneOwner"
        title="작업을 취소했어요"
        description={
          canceled
            ? `${formatWon(canceled.refund)}을 결제한 수단으로 돌려드려요.\n${who}에게는 착수 보상 ${formatWon(canceled.reward)}이 가요.`
            : ""
        }
        actions={
          <Button fullWidth onClick={() => navigate(OWNER_PATHS.activity("done"), { replace: true })}>
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default OwnerWorkCancelPage;
