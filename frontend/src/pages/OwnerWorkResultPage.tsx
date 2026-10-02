import { useParams } from "react-router-dom";
import { DownloadButton, FlowBar, NoteBox, SubScreen, WorkKindIcon } from "../components";
import { OWNER_PATHS, OwnerMissing, flowSteps, useOwnerWork } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerDetailPage.css";
import "./OwnerWorkResultPage.css";

/** 피그마 「지난 결과물 보기」. 원본 파일을 하나씩 받고, 작업 기록을 본다 */
function OwnerWorkResultPage() {
  const { workId = "" } = useParams();
  const back = useBack(OWNER_PATHS.home);
  const work = useOwnerWork(workId);

  if (!work || work.status !== "completed") {
    return (
      <OwnerMissing title="지난 결과물 보기" onBack={back} message="아직 끝나지 않은 작업이에요" />
    );
  }

  const completedHow = work.completedBy === "auto" ? "7일 지나 자동 완료" : "사장님이 직접 확인";
  const completedOn = work.completedOn ? formatMonthDay(work.completedOn) : "";

  return (
    <SubScreen title="지난 결과물 보기" onBack={back}>
      <div className="owner-detail owner-result">
        <div className="owner-detail__work">
          <div className="owner-detail__work-head">
            <WorkKindIcon kind={work.kind} size={22} />
            <h2 className="owner-detail__work-title">{work.title}</h2>
            <span className="owner-result__done">완료</span>
          </div>
          <p className="owner-detail__work-meta">
            {`${work.student.name} 학생\n${completedOn} 완료 (${completedHow})\n작업비 ${formatWon(work.budget)} 정산 완료`}
          </p>
        </div>

        <FlowBar steps={flowSteps("의뢰", 5)} />

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">원본 파일</h2>
          <ul className="owner-result__files">
            {work.files.map((file) => (
              <li key={file.name} className="owner-result__file">
                <span className="owner-result__file-icon" aria-hidden="true">
                  📄
                </span>
                <span className="owner-result__file-info">
                  <span className="owner-result__file-name">{file.name}</span>
                  <span className="owner-result__file-size">{file.size}</span>
                </span>
                <DownloadButton aria-label={`${file.name} 받기`} />
              </li>
            ))}
          </ul>
        </section>

        {work.studentMessage && (
          <NoteBox title={`${work.student.name} 학생의 한마디`} body={work.studentMessage} />
        )}

        {work.history.length > 0 && (
          <div className="owner-result__history">
            <h2 className="owner-result__history-title">작업 기록</h2>
            <ul className="owner-result__history-list">
              {work.history.map((item, i) => (
                <li key={i} className="owner-result__history-item">
                  <span className="owner-result__history-dot" aria-hidden="true" />
                  <span className="owner-result__history-date">{formatMonthDay(item.date)}</span>
                  <span>{item.text}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </SubScreen>
  );
}

export default OwnerWorkResultPage;
