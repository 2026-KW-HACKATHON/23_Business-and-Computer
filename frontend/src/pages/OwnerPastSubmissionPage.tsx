import { useState } from "react";
import { useParams } from "react-router-dom";
import { Button, FlowBar, LoadNotice, NoteBox, ReportSheet, SubScreen, WorkKindIcon } from "../components";
import { chatWorkFlowIndex, chatWorkStageOf, useChatRooms } from "../features/chat";
import {
  OWNER_PATHS,
  OwnerMissing,
  flowSteps,
  parsePositiveId,
  submissionFileName,
  useJobSubmissions,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, koreaDate } from "../lib/date";
import { studentTitle } from "../lib/korean";
import "./OwnerDetailPage.css";
import "./OwnerWorkCheckPage.css";

/**
 * 피그마 「작업 확인 · 지난 초안 (읽기 전용)」 (ADR 0045). 작업 이력의 지난 「초안」 · 「수정안」 줄.
 * 서류 이력(GET /jobs/{id}/submissions)의 그 결과물 파일과 학생 메시지를 읽기만 하고, 버튼은 「확인」 하나.
 * 작업 이름 · 학생 · 수정 횟수 · 지금 단계는 채팅방(GET /me/chat-rooms)에서 읽는다
 */
function OwnerPastSubmissionPage() {
  const { workId, submissionId } = useParams();
  const jobId = parsePositiveId(workId);
  const id = parsePositiveId(submissionId);
  const back = useBack(OWNER_PATHS.chats);
  const { load, reload } = useJobSubmissions(jobId);
  const { load: roomsLoad } = useChatRooms();
  const [reportOpen, setReportOpen] = useState(false);

  if (jobId === undefined || id === undefined || load.status === "notFound" || load.status === "closed") {
    return <OwnerMissing title="지난 결과물" onBack={back} />;
  }
  if (load.status !== "loaded") {
    return (
      <SubScreen title="지난 결과물" onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="결과물을 불러오는 중이에요"
          errorText="결과물을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  const submission = load.data.find((s) => s.submissionId === id);
  if (!submission) return <OwnerMissing title="지난 결과물" onBack={back} />;

  const room = roomsLoad.status === "loaded" ? roomsLoad.rooms.find((r) => r.jobId === jobId) : undefined;
  const stage = room ? chatWorkStageOf(room) : undefined;
  const kind = typeof room?.proposalId === "number" ? "proposal" : "request";
  const noun = submission.submissionType === "REVISION" ? "수정안" : "초안";
  const who = room ? studentTitle(room.counterpartName) : undefined;
  // 「박지은 학생 · 초안 도착 9월 22일 · 수정 1/1」
  const meta = [
    who,
    `${noun} 도착 ${formatMonthDay(koreaDate(submission.submittedAt))}`,
    room ? `수정 ${submission.revisionNumber}/${room.revisionCount}` : undefined,
  ]
    .filter(Boolean)
    .join(" · ");
  const flowIndex = chatWorkFlowIndex(stage);
  const flowSub = stage === "draftArrived" || stage === "revisionArrived" ? "확인해 주세요" : "작업 중";
  const headline = submission.revisionRequest
    ? `이 ${noun}에 수정을 요청했어요`
    : submission.reviewStatus === "APPROVED"
      ? `이 ${noun}으로 완료했어요`
      : `${noun}이 도착했어요`;
  const message = submission.message?.trim();

  return (
    <SubScreen
      title={`지난 ${noun}`}
      onBack={back}
      right={
        <button type="button" className="owner-check__report" onClick={() => setReportOpen(true)}>
          신고
        </button>
      }
      footer={
        <Button fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      <div className="owner-detail">
        <div className="owner-detail__work">
          <div className="owner-detail__work-head">
            <WorkKindIcon kind={kind} size={22} />
            <h2 className="owner-detail__work-title">{room?.jobTitle ?? ""}</h2>
          </div>
          <p className="owner-detail__work-meta">{meta}</p>
        </div>

        {flowIndex !== undefined && (
          <FlowBar
            steps={flowSteps(kind === "proposal" ? "제안" : "의뢰", flowIndex, flowIndex < 5 ? flowSub : undefined)}
          />
        )}

        <h2 className="owner-check__headline">{headline}</h2>

        <section className="owner-detail__section owner-check__files">
          <h2 className="owner-detail__section-title">원본 파일</h2>
          <ul className="owner-check__file-list">
            {submission.fileUrls.map((url) => (
              <li key={url} className="owner-check__file">
                <span className="owner-check__file-name">{submissionFileName(url)}</span>
                <a className="text-button owner-check__download" href={url} target="_blank" rel="noreferrer" download>
                  <span>받기</span>
                </a>
              </li>
            ))}
          </ul>
        </section>

        {message && <NoteBox title={`${who ?? "학생"}의 메세지`} body={message} />}
      </div>

      <ReportSheet open={reportOpen} workTitle={room?.jobTitle ?? ""} onClose={() => setReportOpen(false)} />
    </SubScreen>
  );
}

export default OwnerPastSubmissionPage;
