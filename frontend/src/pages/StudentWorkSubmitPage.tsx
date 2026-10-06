import { useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Dialog,
  FlowBar,
  FormField,
  SubScreen,
  TextAreaField,
  TurnNotice,
} from "../components";
import {
  FilePicker,
  STUDENT_PATHS,
  StudentMissing,
  WorkSummary,
  submitWork,
  useStudentWork,
  workFlowSteps,
} from "../features/student";
import type { WorkFile } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import StudentJobSubmitPage from "./StudentJobSubmitPage";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/**
 * 피그마 「작업 진행 · 제출」. 초안 파일과 사장님께 한마디를 올린다.
 * 보내면 「제출 완료 팝업창」 → 내 활동 (진행 중).
 */
function StudentWorkSubmitPage() {
  const { workId = "" } = useParams();
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <StudentJobSubmitPage jobId={jobId} kind="draft" />
  ) : (
    <SampleWorkSubmit workId={workId} />
  );
}

/** 샘플 작업(알림 · 채팅의 예시)의 초안 제출. 서버 작업은 StudentJobSubmitPage */
function SampleWorkSubmit({ workId }: { workId: string }) {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const work = useStudentWork(workId);
  const [files, setFiles] = useState<WorkFile[]>([]);
  const [message, setMessage] = useState("");
  const [done, setDone] = useState(false);

  if (!work) return <StudentMissing title="초안 제출" onBack={back} />;
  if (!done) {
    if (work.status === "awaitingAgreement") return <Navigate to={STUDENT_PATHS.workStart(work.id)} replace />;
    if (work.status === "revising") return <Navigate to={STUDENT_PATHS.workRevision(work.id)} replace />;
    if (work.status === "submitted") return <Navigate to={STUDENT_PATHS.workSubmitted(work.id)} replace />;
    if (work.status === "completed") return <Navigate to={STUDENT_PATHS.workResult(work.id)} replace />;
    if (work.status === "canceled") return <Navigate to={STUDENT_PATHS.workCanceled(work.id)} replace />;
  }

  const due = formatMonthDay(work.draftDue);

  const submit = () => {
    submitWork(work.id, files, message.trim());
    setDone(true);
  };

  return (
    <SubScreen
      title="초안 제출"
      onBack={back}
      footer={
        <Button tone="student" fullWidth disabled={files.length === 0 || done} onClick={submit}>
          초안 제출하기
        </Button>
      }
    >
      <div className="student-detail">
        <WorkSummary
          kind={work.kind}
          title={work.title}
          meta={`${work.store.name} · 수정 ${work.revisionLimit}회`}
        />

        <FlowBar tone="student" steps={workFlowSteps(work, `${due}까지`)} />

        <TurnNotice tone="student" title={`${due}까지 초안을 올려 주세요`} />

        <FormField label="결과물 파일">
          <FilePicker files={files} onChange={setFiles} hint="PDF·이미지·원본 파일 · 최대 50MB" />
        </FormField>

        <FormField label="사장님께 한마디" wrapsInput>
          <TextAreaField
            value={message}
            maxLength={300}
            placeholder="예: 시안 2가지를 올렸어요. 마음에 드는 쪽을 골라 주세요"
            onChange={setMessage}
          />
        </FormField>

        <div className="student-work__info">
          <strong>완료 전까지는 사장님께 미리보기로만 보여요</strong>
          <p>
            사장님이 완료를 확인하면 원본 파일이 전달되고 작업비가 정산돼요. 7일 동안 답이 없으면
            자동으로 완료돼요.
          </p>
        </div>
      </div>

      <Dialog
        open={done}
        image="doneStudent"
        title="초안을 보냈어요"
        description={"사장님이 확인하면 알림으로 알려 드려요.\n7일 동안 답이 없으면 자동으로 완료돼요."}
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

export default StudentWorkSubmitPage;
