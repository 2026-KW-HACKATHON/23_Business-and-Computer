import { useState } from "react";
import { Navigate, useParams } from "react-router-dom";
import {
  Button,
  DownloadButton,
  FlowBar,
  NoteBox,
  ReportSheet,
  SubScreen,
} from "../components";
import {
  STUDENT_PATHS,
  StudentMissing,
  WorkSummary,
  useStudentWork,
  workFlowSteps,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { StudentJobSubmittedPage } from "./StudentJobStagePages";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/**
 * 피그마 「제출한 초안 보기」. 사장님이 확인하는 동안 낸 파일과 남긴 말을 다시 본다.
 * 사장님에게 문제가 있으면 오른쪽 위 「신고」(메일 문의 안내).
 */
function StudentSubmittedPage() {
  const { workId = "" } = useParams();
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <StudentJobSubmittedPage jobId={jobId} />
  ) : (
    <SampleWork workId={workId} />
  );
}

/** 샘플 작업(알림 · 채팅의 예시). 서버 작업은 StudentJobSubmittedPage */
function SampleWork({ workId }: { workId: string }) {
  const back = useBack(STUDENT_PATHS.home);
  const work = useStudentWork(workId);
  const [reportOpen, setReportOpen] = useState(false);

  if (!work) return <StudentMissing title="제출한 초안" onBack={back} />;
  if (work.status !== "submitted") return <Navigate to={STUDENT_PATHS.workSubmit(work.id)} replace />;

  const stage = work.revisionCount > 0 ? "수정안" : "초안";
  const submittedOn = work.submittedOn ? formatMonthDay(work.submittedOn) : "";
  const autoCompleteOn = work.autoCompleteOn ? formatMonthDay(work.autoCompleteOn) : "";

  return (
    <SubScreen
      title={`제출한 ${stage}`}
      onBack={back}
      right={
        <button type="button" className="student-work__report" onClick={() => setReportOpen(true)}>
          신고
        </button>
      }
      footer={
        <Button tone="student" fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      <div className="student-detail">
        <WorkSummary
          kind={work.kind}
          title={work.title}
          meta={`${work.store.name} · ${submittedOn} 제출 · 수정 ${work.revisionCount}/${work.revisionLimit}`}
        />

        <FlowBar tone="student" steps={workFlowSteps(work, "확인 중")} />

        <div className="student-work__info">
          <strong>사장님이 확인하고 있어요</strong>
          <p>
            사장님이 수정 요청이나 완료 확인을 누르면 알려 드려요. {autoCompleteOn}까지 답이 없으면
            자동으로 완료돼요.
          </p>
        </div>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">원본 파일</h2>
          <ul className="student-work__files">
            {work.files.map((file) => (
              <li key={file.name} className="student-work__file">
                <span aria-hidden="true">📄</span>
                <span className="student-work__file-info">
                  <strong>{file.name}</strong>
                  <small>{file.size}</small>
                </span>
                <DownloadButton aria-label={`${file.name} 받기`} />
              </li>
            ))}
          </ul>
        </section>

        {work.myMessage && <NoteBox title="내가 남긴 한마디" body={work.myMessage} />}
      </div>

      <ReportSheet
        tone="student"
        open={reportOpen}
        workTitle={work.title}
        onClose={() => setReportOpen(false)}
      />
    </SubScreen>
  );
}

export default StudentSubmittedPage;
