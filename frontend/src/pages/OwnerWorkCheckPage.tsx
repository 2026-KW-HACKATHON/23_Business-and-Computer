import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  FlowBar,
  NoteBox,
  SubScreen,
  TextButton,
  TurnNotice,
  WorkKindIcon,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  ReportSheet,
  completeOwnerWork,
  flowSteps,
  useOwnerWork,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import "./OwnerDetailPage.css";
import "./OwnerWorkCheckPage.css";

/**
 * 피그마 「작업 확인 · 초안」 · 「작업 확인 · 수정안」.
 * 남은 수정이 없으면 「수정 요청」 버튼이 없고 완료 확인만 할 수 있다.
 */
function OwnerWorkCheckPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const work = useOwnerWork(workId);
  const [reportOpen, setReportOpen] = useState(false);

  if (!work || work.status !== "submitted") {
    return <OwnerMissing title="작업 확인" onBack={back} message="확인할 결과물이 아직 없어요" />;
  }

  const isRevision = work.revisionCount > 0;
  const stage = isRevision ? "수정안" : "초안";
  const remaining = work.revisionLimit - work.revisionCount;
  const submittedOn = work.submittedOn ? formatMonthDay(work.submittedOn) : "";
  const autoCompleteOn = work.autoCompleteOn ? formatMonthDay(work.autoCompleteOn) : "";

  return (
    <SubScreen
      title={`${stage} 확인`}
      onBack={back}
      right={
        <button type="button" className="owner-check__report" onClick={() => setReportOpen(true)}>
          신고
        </button>
      }
      footer={
        <div className="owner-detail__actions">
          {remaining > 0 && (
            <Button variant="secondary" onClick={() => navigate(OWNER_PATHS.workRevision(work.id))}>
              수정 요청
            </Button>
          )}
          <Button
            onClick={() => {
              completeOwnerWork(work.id);
              navigate(OWNER_PATHS.workReview(work.id), { replace: true });
            }}
          >
            완료 확인
          </Button>
        </div>
      }
    >
      <div className="owner-detail">
        <div className="owner-detail__work">
          <div className="owner-detail__work-head">
            <WorkKindIcon kind={work.kind} size={22} />
            <h2 className="owner-detail__work-title">{work.title}</h2>
          </div>
          <p className="owner-detail__work-meta">
            {`${work.student.name} 학생 · ${stage} 도착 ${submittedOn} · 수정 ${work.revisionCount}/${work.revisionLimit}`}
          </p>
        </div>

        <FlowBar steps={flowSteps("의뢰", isRevision ? 3 : 2, "확인해 주세요")} />

        <div className="owner-check__notice">
          <h2 className="owner-check__headline">{stage}이 도착했어요</h2>
          <TurnNotice
            tone="owner"
            title={`${autoCompleteOn}까지 확인해 주세요`}
            description={`답이 없으면 자동으로 완료돼요\n${
              remaining > 0 ? `수정 요청 ${remaining}회 남음` : "남은 수정 요청이 없어요"
            }`}
          />
        </div>

        <section className="owner-detail__section owner-check__files">
          <h2 className="owner-detail__section-title">원본 파일</h2>
          <ul className="owner-check__file-list">
            {work.files.map((file) => (
              <li key={file.name} className="owner-check__file">
                <span>{file.name}</span>
                <TextButton showChevron={false}>받기</TextButton>
              </li>
            ))}
          </ul>
        </section>

        {work.studentMessage && (
          <NoteBox title={`${work.student.name} 학생의 메세지`} body={work.studentMessage} />
        )}
      </div>
      <ReportSheet open={reportOpen} workTitle={work.title} onClose={() => setReportOpen(false)} />
    </SubScreen>
  );
}

export default OwnerWorkCheckPage;
