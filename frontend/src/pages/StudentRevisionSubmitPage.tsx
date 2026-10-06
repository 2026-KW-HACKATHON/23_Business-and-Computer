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
import { formatWon } from "../lib/money";
import StudentJobSubmitPage from "./StudentJobSubmitPage";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/**
 * 피그마 「수정안 작성하기 수정안 제출」. 사장님 수정 요청을 보며 최종본 파일과 메시지를 올린다.
 * 보내면 「수정안 제출 완료 팝업창」 → 내 활동 (진행 중).
 */
function StudentRevisionSubmitPage() {
  const { workId = "" } = useParams();
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <StudentJobSubmitPage jobId={jobId} kind="revision" />
  ) : (
    <SampleWorkSubmit workId={workId} />
  );
}

/** 샘플 작업(알림 · 채팅의 예시)의 수정안 제출. 서버 작업은 StudentJobSubmitPage */
function SampleWorkSubmit({ workId }: { workId: string }) {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const work = useStudentWork(workId);
  const [files, setFiles] = useState<WorkFile[]>([]);
  const [message, setMessage] = useState("");
  const [done, setDone] = useState(false);

  if (!work) return <StudentMissing title="수정안 제출" onBack={back} />;
  if (!done && work.status !== "revising") {
    return <Navigate to={STUDENT_PATHS.workSubmit(work.id)} replace />;
  }
  if (!done && !work.revisionRequest) {
    return <StudentMissing title="수정안 제출" onBack={back} message="수정 요청 내용이 없어요" />;
  }
  const due = formatMonthDay(work.finalDue);
  const last = work.revisionCount >= work.revisionLimit;

  const submit = () => {
    submitWork(work.id, files, message.trim());
    setDone(true);
  };

  return (
    <SubScreen
      title="수정안 제출"
      onBack={back}
      footer={
        <Button tone="student" fullWidth disabled={files.length === 0 || done} onClick={submit}>
          수정안 제출하기
        </Button>
      }
    >
      <div className="student-detail">
        <WorkSummary kind={work.kind} title={work.title} meta={`${work.store.name} 사장님`} />

        <FlowBar tone="student" steps={workFlowSteps(work, `${due}까지`)} />

        <TurnNotice tone="student" title={`${due}까지 수정안을 올려 주세요`} />

        {work.revisionRequest && (
          <div className="student-work__quote">
            <strong>{work.store.name} 사장님의 수정 요청</strong>
            <small>{formatMonthDay(work.revisionRequest.requestedOn)}</small>
            <p>{work.revisionRequest.text}</p>
          </div>
        )}

        <FormField label="최종본 파일">
          <FilePicker files={files} onChange={setFiles} hint="인쇄용 PDF와 원본 파일을 함께 올려 주세요" />
        </FormField>

        <FormField label="사장님께 함께 보낼 메세지" wrapsInput>
          <TextAreaField
            value={message}
            maxLength={300}
            placeholder="예: 말씀하신 대로 사진을 밝게 고쳤어요"
            onChange={setMessage}
          />
        </FormField>

        <div className="student-work__info">
          {last && <strong>최종본을 보내면 더 이상 수정 요청이 없어요</strong>}
          <p>
            사장님이 완료를 확인하면 작업비 {formatWon(work.budget)}이 정산돼요. 7일 동안 답이 없으면
            자동으로 완료돼요.
          </p>
        </div>
      </div>

      <Dialog
        open={done}
        image="doneStudent"
        title="수정안을 보냈어요"
        description="사장님이 완료를 확인하면 작업비가 정산돼요."
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

export default StudentRevisionSubmitPage;
