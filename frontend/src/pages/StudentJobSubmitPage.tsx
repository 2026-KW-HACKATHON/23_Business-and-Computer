import { useRef, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import {
  Button,
  Dialog,
  FlowBar,
  FormField,
  LoadNotice,
  SubScreen,
  TextAreaField,
  TurnNotice,
} from "../components";
import {
  FilePicker,
  MAX_SUBMISSION_FILES,
  STUDENT_PATHS,
  SUBMISSION_FILE_ACCEPT,
  SUBMISSION_FILE_HINT,
  StudentMissing,
  WorkSummary,
  isSubmittableFile,
  progressFlowSteps,
  progressMeta,
  progressStagePath,
  sendSubmission,
  useProgressJobs,
} from "../features/student";
import type { ProgressJob, SubmissionKind, WorkFile } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

type SendError = "fileFailed" | "retry";

const SEND_ERROR_TEXT: Record<SendError, string> = {
  fileFailed: "파일을 올리지 못했어요. 형식과 크기를 확인하고 다시 시도해 주세요",
  retry: "잠시 후 다시 시도해 주세요",
};

/**
 * 나와 매칭된 진행 중 작업(GET /me/jobs?status=MATCHED)의 초안 · 수정안 제출 (ADR 0032).
 * 피그마 「작업 진행 · 제출」 · 「수정안 작성하기 수정안 제출」.
 * 파일을 하나씩 올린 뒤(POST /jobs/{id}/submission/uploads) 초안은 POST /jobs/{id}/submission,
 * 수정안은 POST /jobs/{id}/submission/revisions 로 낸다. 지금 단계가 아니면 그 단계 화면으로 보낸다.
 */
function StudentJobSubmitPage({ jobId, kind }: { jobId: number; kind: SubmissionKind }) {
  const back = useBack(STUDENT_PATHS.home);
  const { load, reload } = useProgressJobs();
  const title = kind === "draft" ? "초안 제출" : "수정안 제출";

  if (load.status !== "loaded") {
    return (
      <SubScreen title={title} onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="작업을 불러오는 중이에요"
          errorText="작업을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  const job = load.jobs.find((j) => j.jobId === jobId);
  if (!job) return <StudentMissing title={title} onBack={back} message="진행 중인 작업이 아니에요" />;
  return <SubmitForm job={job} kind={kind} title={title} onBack={back} />;
}

function SubmitForm({
  job,
  kind,
  title,
  onBack,
}: {
  job: ProgressJob;
  kind: SubmissionKind;
  title: string;
  onBack: () => void;
}) {
  const navigate = useNavigate();
  const [files, setFiles] = useState<WorkFile[]>([]);
  const [fileNotice, setFileNotice] = useState(false);
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<SendError | null>(null);
  const [done, setDone] = useState(false);
  // 빠른 두 번 누름에도 한 번만 보낸다
  const inFlight = useRef(false);

  const draft = kind === "draft";
  if (!done && job.stage !== (draft ? "drafting" : "revising")) {
    return <Navigate to={progressStagePath(job)} replace />;
  }
  const id = String(job.jobId);
  const due = formatMonthDay(draft ? job.draftDeadline : job.finalDeadline);
  const canSend = files.length > 0 && message.trim() !== "" && !sending && !done;

  // 올릴 수 없는 형식 · 크기와 10개 넘는 파일은 빼고 안내한다
  const pickFiles = (next: WorkFile[]) => {
    const accepted = next
      .filter((f) => !f.file || isSubmittableFile(f.file))
      .slice(0, MAX_SUBMISSION_FILES);
    setFileNotice(accepted.length < next.length);
    setFiles(accepted);
    setSendError(null);
  };

  const send = async () => {
    if (inFlight.current || !canSend) return;
    inFlight.current = true;
    setSending(true);
    setSendError(null);
    const picked = files.flatMap((f) => (f.file ? [f.file] : []));
    const result = await sendSubmission(job.jobId, kind, picked, message);
    inFlight.current = false;
    setSending(false);
    switch (result.status) {
      case "sent":
        setDone(true);
        return;
      case "unauthorized":
        navigate("/login", { replace: true });
        return;
      case "alreadySubmitted":
        window.alert("이미 초안을 냈어요");
        navigate(STUDENT_PATHS.workSubmitted(id), { replace: true });
        return;
      case "notRequested":
        window.alert("수정 요청이 와야 수정안을 낼 수 있어요");
        navigate(STUDENT_PATHS.activity("inProgress"), { replace: true });
        return;
      case "notAvailable":
        window.alert("지금은 결과물을 낼 수 없는 작업이에요");
        navigate(STUDENT_PATHS.activity("inProgress"), { replace: true });
        return;
      default:
        setSendError(result.status);
    }
  };

  return (
    <SubScreen
      title={title}
      onBack={onBack}
      footer={
        <>
          {sendError && (
            <p className="student-detail__send-error" role="alert">
              {SEND_ERROR_TEXT[sendError]}
            </p>
          )}
          <Button tone="student" fullWidth disabled={!canSend} onClick={() => void send()}>
            {sending ? "보내는 중..." : draft ? "초안 제출하기" : "수정안 제출하기"}
          </Button>
        </>
      }
    >
      <div className="student-detail">
        <WorkSummary
          kind={job.kind}
          title={job.title}
          meta={
            draft
              ? progressMeta(job, `수정 ${job.revisionLimit}회`)
              : job.storeName
                ? `${job.storeName} 사장님`
                : ""
          }
        />

        <FlowBar tone="student" steps={progressFlowSteps(job, `${due}까지`)} />

        <TurnNotice tone="student" title={`${due}까지 ${draft ? "초안" : "수정안"}을 올려 주세요`} />

        {!draft && (
          <div className="student-work__quote">
            <strong>{job.storeName ? `${job.storeName} 사장님의 수정 요청` : "사장님의 수정 요청"}</strong>
            <p>수정 요청 내용은 곧 여기서 볼 수 있어요</p>
          </div>
        )}

        <FormField label={draft ? "결과물 파일" : "최종본 파일"}>
          <FilePicker
            files={files}
            onChange={pickFiles}
            hint={SUBMISSION_FILE_HINT}
            accept={SUBMISSION_FILE_ACCEPT}
          />
        </FormField>
        {fileNotice && (
          <p className="student-work__file-notice" role="status">
            올릴 수 없는 파일은 뺐어요 ({SUBMISSION_FILE_HINT})
          </p>
        )}

        <FormField label={draft ? "사장님께 한마디" : "사장님께 함께 보낼 메세지"} hint="꼭 적어 주세요" wrapsInput>
          <TextAreaField
            value={message}
            maxLength={300}
            placeholder={
              draft
                ? "예: 시안 2가지를 올렸어요. 마음에 드는 쪽을 골라 주세요"
                : "예: 말씀하신 대로 사진을 밝게 고쳤어요"
            }
            onChange={(value) => {
              setMessage(value);
              setSendError(null);
            }}
          />
        </FormField>

        <div className="student-work__info">
          {draft && <strong>완료 전까지는 사장님께 미리보기로만 보여요</strong>}
          <p>
            {draft
              ? "사장님이 완료를 확인하면 원본 파일이 전달되고 작업비가 정산돼요. 7일 동안 답이 없으면 자동으로 완료돼요."
              : `사장님이 완료를 확인하면 작업비 ${formatWon(job.budget)}이 정산돼요. 7일 동안 답이 없으면 자동으로 완료돼요.`}
          </p>
        </div>
      </div>

      <Dialog
        open={done}
        image="doneStudent"
        title={draft ? "초안을 보냈어요" : "수정안을 보냈어요"}
        description={
          draft
            ? "사장님이 확인하면 알림으로 알려 드려요.\n7일 동안 답이 없으면 자동으로 완료돼요."
            : "사장님이 완료를 확인하면 작업비가 정산돼요."
        }
        actions={
          <Button
            tone="student"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.activity("inProgress"), { replace: true })}
          >
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default StudentJobSubmitPage;
