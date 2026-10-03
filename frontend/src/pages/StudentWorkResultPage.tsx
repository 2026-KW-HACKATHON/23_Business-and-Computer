import { useNavigate, useParams } from "react-router-dom";
import {
  AttachmentTiles,
  DownloadButton,
  FlowBar,
  NoteBox,
  SubScreen,
  TextButton,
} from "../components";
import {
  STUDENT_PATHS,
  StudentMissing,
  WorkSummary,
  flowSteps,
  useStudentWork,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/**
 * 피그마 「내 결과물 보기」. 최종 결과물 · 원본 파일 · 사장님 후기 · 작업 기록.
 * 완료된 작업은 「내 작업물 모아보기」에도 담긴다.
 */
function StudentWorkResultPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const work = useStudentWork(workId);

  if (!work || work.status !== "completed") {
    return <StudentMissing title="내 결과물" onBack={back} message="아직 끝나지 않은 작업이에요" />;
  }

  const completedHow = work.completedBy === "auto" ? "7일 지나 자동 완료" : "사장님이 직접 확인";
  const completedOn = work.completedOn ? formatMonthDay(work.completedOn) : "";

  return (
    <SubScreen title="내 결과물" onBack={back}>
      <div className="student-detail">
        <WorkSummary
          kind={work.kind}
          title={work.title}
          right={<span className="student-work__chip">완료</span>}
          meta={`${work.store.name} · ${completedOn} 완료 (${completedHow})\n작업비 ${formatWon(work.budget)} 정산 완료`}
        />

        <FlowBar tone="student" steps={flowSteps(work.kind === "proposal" ? "제안" : "의뢰", 5)} />

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">최종 수정안</h2>
          {work.resultSummary && <p className="student-detail__text">{work.resultSummary}</p>}
          <AttachmentTiles names={work.files.map((f) => f.name)} height={110} />
          <p className="student-work__hint">완료된 작업이라 「내 작업물 모아보기」에 자동으로 담겼어요</p>
        </section>

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

        {work.review ? (
          <div className="student-work__review">
            <div className="student-work__review-head">
              <strong>{work.store.name} 사장님의 후기</strong>
              <span>★ {work.review.rating.toFixed(1)}</span>
            </div>
            <p>{work.review.text}</p>
            <TextButton onClick={() => navigate(STUDENT_PATHS.workReview(work.id))}>받은 후기 보기</TextButton>
          </div>
        ) : (
          work.myMessage && <NoteBox title="내가 남긴 한마디" body={work.myMessage} />
        )}

        {work.history.length > 0 && (
          <div className="student-work__history">
            <h2 className="student-detail__section-title">작업 기록</h2>
            <ul className="student-work__history-list">
              {work.history.map((item, i) => (
                <li key={i} className="student-work__history-item">
                  <span className="student-work__history-dot" aria-hidden="true" />
                  <span className="student-work__history-date">{formatMonthDay(item.date)}</span>
                  <span>{item.text}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <p className="student-detail__footnote">
          사장님이 결과물을 받고 7일 동안 확인하지 않으면 자동으로 완료되고 작업비가 정산돼요.
        </p>
      </div>
    </SubScreen>
  );
}

export default StudentWorkResultPage;
