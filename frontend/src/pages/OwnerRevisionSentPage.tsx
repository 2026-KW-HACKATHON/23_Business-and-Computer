import { useParams, useSearchParams } from "react-router-dom";
import { Button, LoadNotice, ReferencePhotos, SubScreen, WorkKindIcon } from "../components";
import { chatWorkStageOf, useChatRooms } from "../features/chat";
import { OWNER_PATHS, OwnerMissing, parsePositiveId, useJobSubmissions } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, koreaDate } from "../lib/date";
import { studentTitle } from "../lib/korean";
import "./OwnerRevisionPage.css";

/**
 * 피그마 「보낸 수정 요청 보기 (사장님)」 (ADR 0045). 작업 이력의 「수정 요청」 줄. 수정 요청 화면과 같은
 * 틀로, 내가 보낸 요청 내용과 참고 사진을 읽기만 한다. 서류 이력(GET /jobs/{id}/submissions)에서 주소의
 * ?submission= 결과물에 보낸 요청을, 없으면 마지막으로 보낸 요청을 보인다. 작업 이름 · 학생 · 수정 횟수 ·
 * 최종 마감은 채팅방(GET /me/chat-rooms)에서 읽는다
 */
function OwnerRevisionSentPage() {
  const { workId } = useParams();
  const [params] = useSearchParams();
  const jobId = parsePositiveId(workId);
  const submissionId = parsePositiveId(params.get("submission") ?? undefined);
  const back = useBack(OWNER_PATHS.chats);
  const { load, reload } = useJobSubmissions(jobId);
  const { load: roomsLoad } = useChatRooms();

  if (jobId === undefined) return <OwnerMissing title="보낸 수정 요청" onBack={back} />;

  // 수정 요청을 보낸 결과물 (오래된 것부터)
  const requested =
    load.status === "loaded"
      ? [...load.data].sort((a, b) => a.revisionNumber - b.revisionNumber).filter((s) => s.revisionRequest)
      : [];
  const newest = requested.length > 0 ? requested[requested.length - 1] : undefined;
  const target = submissionId === undefined ? newest : requested.find((s) => s.submissionId === submissionId);
  const request = target?.revisionRequest ?? undefined;
  const room = roomsLoad.status === "loaded" ? roomsLoad.rooms.find((r) => r.jobId === jobId) : undefined;
  // 학생이 지금 고치고 있는 요청이면 최종 마감을 알린다
  const current = room !== undefined && target !== undefined && target === newest && chatWorkStageOf(room) === "revising";
  // 「박지은 학생, 9월 23일 수정 요청, 수정 1/1」
  const meta = [
    room ? studentTitle(room.counterpartName) : undefined,
    request ? `${formatMonthDay(koreaDate(request.requestedAt))} 수정 요청` : undefined,
    target && room ? `수정 ${target.revisionNumber + 1}/${room.revisionCount}` : undefined,
  ]
    .filter(Boolean)
    .join(", ");
  const photos = request?.referenceImageUrls ?? [];

  return (
    <SubScreen
      title="보낸 수정 요청"
      onBack={back}
      footer={
        <Button fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      {(load.status === "loading" || load.status === "error") && (
        <LoadNotice
          status={load.status}
          loadingText="수정 요청을 불러오는 중이에요"
          errorText="수정 요청을 불러오지 못했어요"
          onRetry={reload}
        />
      )}
      {load.status !== "loading" && load.status !== "error" && !request && (
        <p className="owner-revision__note">보낸 수정 요청이 없어요</p>
      )}
      {request && (
        <div className="owner-revision">
          <section className="owner-revision__work">
            <div className="owner-revision__work-head">
              <WorkKindIcon kind={typeof room?.proposalId === "number" ? "proposal" : "request"} size={22} />
              <h2 className="owner-revision__work-title">{room?.jobTitle ?? ""}</h2>
            </div>
            {meta && <p className="owner-revision__meta">{meta}</p>}
          </section>

          <div className="owner-revision__intro">
            <h2 className="owner-revision__title">이렇게 고쳐 달라고 했어요</h2>
            <p className="owner-revision__description">추가 자료나 질문은 채팅으로 보내 주세요.</p>
          </div>

          <section className="request-field">
            <h3 className="request-field__label">요청 내용</h3>
            <div className="request-field__textarea-box">
              <p className="owner-revision__sent-text">{request.message?.trim() || "적은 내용이 없어요"}</p>
            </div>
          </section>

          {photos.length > 0 && (
            <section className="request-field">
              <h3 className="request-field__label">참고 사진</h3>
              <ReferencePhotos urls={photos} />
            </section>
          )}

          {current && room && (
            <p className="owner-revision__note">학생은 최종 마감({formatMonthDay(room.finalDeadline)})까지 수정안을 보내요</p>
          )}
        </div>
      )}
    </SubScreen>
  );
}

export default OwnerRevisionSentPage;
