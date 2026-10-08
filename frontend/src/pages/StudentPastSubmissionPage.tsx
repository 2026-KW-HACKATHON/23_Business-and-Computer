import { useParams } from "react-router-dom";
import { Button, DownloadButton, FlowBar, LoadNotice, NoteBox, ReferencePhotos, SubScreen } from "../components";
import { chatWorkFlowIndex, chatWorkStageOf, useChatRooms } from "../features/chat";
import {
  STUDENT_PATHS,
  StudentMissing,
  WorkSummary,
  flowSteps,
  submissionDay,
  useSubmissionHistory,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { fileNameFromUrl } from "../lib/fileUrl";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/** 숫자로만 된 id. 아니면 undefined */
const idOf = (value: string | undefined) => (value && /^[1-9][0-9]*$/.test(value) ? Number(value) : undefined);

/**
 * 작업 이력의 지난 서류 (학생, ADR 0045). 서류 이력(GET /jobs/{id}/submissions)의 그 결과물을 읽기만 한다.
 * view 가 submission 이면 피그마 「제출한 초안 보기」 · 「제출한 수정안 보기」처럼 낸 파일과 남긴 한마디,
 * request 면 「수정 요청 확인」처럼 그 결과물에 받은 사장님 수정 요청과 내가 보낸 파일. 버튼은 「확인」 하나.
 * 작업 이름 · 가게 · 수정 횟수 · 지금 단계는 채팅방(GET /me/chat-rooms)에서 읽는다
 */
function StudentPastSubmissionPage({ view }: { view: "submission" | "request" }) {
  const { workId, submissionId } = useParams();
  const jobId = idOf(workId);
  const id = idOf(submissionId);
  const back = useBack(STUDENT_PATHS.chats);
  const { load, reload } = useSubmissionHistory(jobId ?? 0);
  const { load: roomsLoad } = useChatRooms();
  const fallbackTitle = view === "request" ? "받은 수정 요청" : "제출한 결과물";

  if (jobId === undefined || id === undefined || load.status === "notFound") {
    return <StudentMissing title={fallbackTitle} onBack={back} />;
  }
  if (load.status !== "loaded") {
    return (
      <SubScreen title={fallbackTitle} onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status === "loading" ? "loading" : "error"}
          loadingText="낸 결과물을 불러오는 중이에요"
          errorText="낸 결과물을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  const submission = load.submissions.find((s) => s.submissionId === id);
  const request = submission?.revisionRequest ?? undefined;
  if (!submission || (view === "request" && !request)) {
    return <StudentMissing title={fallbackTitle} onBack={back} />;
  }

  const room = roomsLoad.status === "loaded" ? roomsLoad.rooms.find((r) => r.jobId === jobId) : undefined;
  const stage = room ? chatWorkStageOf(room) : undefined;
  const kind = typeof room?.proposalId === "number" ? "proposal" : "request";
  const noun = submission.submissionType === "REVISION" ? "수정안" : "초안";
  const owner = room ? `${room.counterpartName} 사장님` : "사장님";
  const sentOn = formatMonthDay(submissionDay(submission.submittedAt));
  // 「가게 · 9월 22일 제출 · 수정 0/1」
  const meta = [room?.counterpartName, `${sentOn} 제출`, room ? `수정 ${submission.revisionNumber}/${room.revisionCount}` : undefined]
    .filter(Boolean)
    .join(" · ");
  const flowIndex = chatWorkFlowIndex(stage);
  const flowSub = stage === "draftArrived" || stage === "revisionArrived" ? "확인 중" : "작업 중";
  const note = submission.message?.trim();
  const photos = request?.referenceImageUrls ?? [];
  const files = (
    <ul className="student-work__files">
      {submission.fileUrls.map((url) => {
        const name = fileNameFromUrl(url);
        return (
          <li key={url} className="student-work__file">
            <span aria-hidden="true">📄</span>
            <span className="student-work__file-info">
              <strong>{name}</strong>
              {view === "request" && <small>{sentOn} 보냄</small>}
            </span>
            <DownloadButton href={url} aria-label={`${name} 받기`} />
          </li>
        );
      })}
    </ul>
  );

  return (
    <SubScreen
      title={view === "request" ? "받은 수정 요청" : `제출한 ${noun}`}
      onBack={back}
      footer={
        <Button tone="student" fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      <div className="student-detail">
        <WorkSummary kind={kind} title={room?.jobTitle ?? ""} meta={meta} />

        {flowIndex !== undefined && (
          <FlowBar
            tone="student"
            steps={flowSteps(kind === "proposal" ? "제안" : "의뢰", flowIndex, flowIndex < 5 ? flowSub : undefined)}
          />
        )}

        {view === "request" && request ? (
          <>
            <section className="student-detail__section">
              <h2 className="student-detail__section-title">{owner}의 수정 요청</h2>
              <div className="student-work__quote">
                <small>{formatMonthDay(submissionDay(request.requestedAt))}</small>
                <p>{request.message?.trim() || "사장님이 적은 내용이 없어요"}</p>
                {photos.length > 0 && (
                  <>
                    <span className="student-work__label">참고 사진</span>
                    <ReferencePhotos urls={photos} />
                  </>
                )}
              </div>
            </section>

            <section className="student-detail__section">
              <h2 className="student-detail__section-title">내가 보낸 {noun}</h2>
              {files}
            </section>
          </>
        ) : (
          <>
            {request && (
              <div className="student-work__info">
                <strong>사장님이 이 {noun}에 수정을 요청했어요</strong>
              </div>
            )}

            <section className="student-detail__section">
              <h2 className="student-detail__section-title">원본 파일</h2>
              {files}
            </section>

            {note && <NoteBox title="내가 남긴 한마디" body={note} />}
          </>
        )}
      </div>
    </SubScreen>
  );
}

export default StudentPastSubmissionPage;
