import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  AttachmentTiles,
  Button,
  DownloadButton,
  FlowBar,
  SubScreen,
  TurnNotice,
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
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/**
 * 피그마 「수정 요청 확인」. 사장님이 보낸 수정 요청과 내가 보낸 초안을 보고
 * 「수정안 작성하기」로 넘어간다. 질문은 「문의하기」(채팅방).
 */
function StudentRevisionPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const work = useStudentWork(workId);

  if (!work) return <StudentMissing title="수정 요청 확인" onBack={back} />;
  if (work.status !== "revising") return <Navigate to={STUDENT_PATHS.workSubmit(work.id)} replace />;
  if (!work.revisionRequest) {
    return <StudentMissing title="수정 요청 확인" onBack={back} message="수정 요청 내용이 없어요" />;
  }
  const request = work.revisionRequest;
  const due = formatMonthDay(work.finalDue);
  const last = work.revisionCount >= work.revisionLimit;

  return (
    <SubScreen
      title="수정 요청 확인"
      onBack={back}
      footer={
        <div className="student-detail__actions">
          <Button variant="secondary" onClick={() => navigate(STUDENT_PATHS.chat(work.id))}>
            문의하기
          </Button>
          <Button tone="student" onClick={() => navigate(STUDENT_PATHS.workRevisionSubmit(work.id))}>
            수정안 작성하기
          </Button>
        </div>
      }
    >
      <div className="student-detail">
        <WorkSummary kind={work.kind} title={work.title} meta={`${work.store.name} 사장님`} />

        <FlowBar tone="student" steps={workFlowSteps(work, `${due}까지`)} />

        <TurnNotice
          tone="student"
          title={`${due}까지 수정안을 올려 주세요`}
          description={last ? "이번이 마지막 수정이에요" : undefined}
        />

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">{work.store.name} 사장님의 수정 요청</h2>
          <div className="student-work__quote">
            <small>{formatMonthDay(request.requestedOn)}</small>
            <p>{request.text}</p>
            {request.attachments.length > 0 && (
              <>
                <span className="student-work__label">참고 사진</span>
                <AttachmentTiles names={request.attachments} height={90} />
              </>
            )}
          </div>
        </section>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">내가 보낸 초안</h2>
          <ul className="student-work__files">
            {work.files.map((file) => (
              <li key={file.name} className="student-work__file">
                <span aria-hidden="true">📄</span>
                <span className="student-work__file-info">
                  <strong>{file.name}</strong>
                  <small>{work.submittedOn ? `${formatMonthDay(work.submittedOn)} 보냄` : file.size}</small>
                </span>
                <DownloadButton aria-label={`${file.name} 받기`} />
              </li>
            ))}
          </ul>
        </section>
      </div>
    </SubScreen>
  );
}

export default StudentRevisionPage;
