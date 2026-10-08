import { useState } from "react";
import type { ChangeEvent, FormEvent, MouseEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { LoadNotice, ReportSheet, RoleAvatar, SubScreen } from "../components";
import {
  ChatPhotoViewer,
  ChatWorkCard,
  canCancelChatWork,
  canReportChatWork,
  chatWorkStageOf,
  chatWorkStatusText,
  attachmentDetailText,
  isAttachmentExpired,
  useChatRoom,
  useScrollToLatest,
} from "../features/chat";
import type { ChatMessage, ChatWorkTroubleItem } from "../features/chat";
import {
  OWNER_PATHS,
  isOwnerWorkReviewed,
  useOwnerClosedJobs,
  useOwnerProgressJobs,
  useProposalJobIds,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { ATTACHMENT_ACCEPT } from "../lib/attachmentFormats";
import { formatDayChip, formatMonthDay } from "../lib/date";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";
import "./OwnerChatRoomPage.css";

const timeOf = (iso: string) => {
  const date = new Date(iso);
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
};

const dayKey = (iso: string) => new Date(iso).toDateString();

/** 피그마 「채팅방 (사장님)」. 작업 하나에 채팅방 하나. 방이 바뀌면 새로 그린다 */
function OwnerChatRoomPage() {
  const { roomId = "" } = useParams();
  return <OwnerChatRoom key={roomId} roomId={roomId} />;
}

function OwnerChatRoom({ roomId }: { roomId: string }) {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.chats);
  const { load, messages, reload, send, sendAttachment, resend, openExpiredAttachment, refreshAttachment } =
    useChatRoom(roomId,
    OWNER_PATHS.chats,
  );
  const [draft, setDraft] = useState("");
  const [reportOpen, setReportOpen] = useState(false);
  // 크게 보는 사진 (메시지의 clientMessageId)
  const [photoKey, setPhotoKey] = useState<string | null>(null);
  // 처음 들어올 때, 맨 아래 근처에서 새 메시지를 받을 때, 내가 보낼 때 맨 아래로
  const endRef = useScrollToLatest(messages);
  // 받은 제안의 의뢰면 제안에서 시작한 작업 (값은 제안 id)
  const proposalJobIds = useProposalJobIds();
  // 도착한 결과물이 초안인지 수정안인지는 진행 중 목록으로, 후기를 남겼는지는 끝난 목록으로
  const { load: progressLoad } = useOwnerProgressJobs();
  const { load: closedLoad } = useOwnerClosedJobs();

  // 사진은 앱 안에서 크게 본다. 주소가 만료됐으면 새로 받아 바꿔 끼운다
  const showPhoto = (message: ChatMessage) => {
    if (isAttachmentExpired(message)) refreshAttachment(message);
    setPhotoKey(message.clientMessageId);
  };

  if (load.status !== "loaded") {
    return (
      <SubScreen title="채팅" onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="채팅방을 불러오는 중이에요"
          errorText="채팅방을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }

  const { room } = load;
  const partnerName = studentTitle(room.counterpartName);
  const id = String(room.jobId);
  const matched = progressLoad.status === "loaded" ? progressLoad.jobs.find((job) => job.jobId === room.jobId) : undefined;
  const stage = chatWorkStageOf(room, matched);
  const proposalId = proposalJobIds.get(room.jobId);
  const kind = proposalId !== undefined ? "proposal" : "request";
  const reviewed =
    isOwnerWorkReviewed(id) ||
    (closedLoad.status === "loaded" && closedLoad.jobs.some((job) => job.jobId === room.jobId && job.reviewed));
  // 지금 할 일: 도착한 결과물 확인, 끝났으면 (끝난 목록을 불러온 뒤) 아직 남기지 않은 후기
  const action =
    stage === "draftArrived" || stage === "revisionArrived"
      ? {
          label: stage === "draftArrived" ? "초안 확인하기" : "수정안 확인하기",
          onClick: () => navigate(OWNER_PATHS.workCheck(id)),
        }
      : stage === "completed" && closedLoad.status === "loaded" && !reviewed
        ? { label: "후기 남기기", onClick: () => navigate(OWNER_PATHS.workReview(id)) }
        : undefined;
  // 「문제가 있나요?」: 작업 취소는 결과물을 하나도 받기 전에만, 신고는 작업 중에만
  const trouble: ChatWorkTroubleItem[] = [
    ...(canCancelChatWork(room) ? [{ label: "작업 취소", onSelect: () => navigate(OWNER_PATHS.workCancel(id)) }] : []),
    ...(canReportChatWork(room) ? [{ label: "문제 신고", onSelect: () => setReportOpen(true) }] : []),
  ];

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const text = draft.trim();
    if (!text) return;
    send(text);
    setDraft("");
  };

  // 한 번에 하나. 같은 파일을 다시 고를 수 있게 고른 값은 바로 비운다
  const handleFile = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (file) sendAttachment(file);
  };

  return (
    <SubScreen
      title={
        <span className="owner-chat__partner">
          <RoleAvatar role="student" size={32} />
          <span className="owner-chat__partner-text">
            <strong>{partnerName}</strong>
            <small>{room.jobTitle}</small>
          </span>
        </span>
      }
      onBack={back}
      footer={
        <form className="owner-chat__composer" onSubmit={handleSubmit}>
          <label className="owner-chat__attach">
            <span aria-hidden="true">+</span>
            <input type="file" accept={ATTACHMENT_ACCEPT} aria-label="사진·파일 보내기" onChange={handleFile} />
          </label>
          <input
            className="owner-chat__input"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            maxLength={5000}
            placeholder="작업 질문이나 자료 요청을 적어 주세요"
            aria-label="메시지"
          />
          <button type="submit" className="owner-chat__send" aria-label="보내기">
            →
          </button>
        </form>
      }
    >
      <div className="owner-chat">
        <ChatWorkCard
          role="owner"
          kind={kind}
          title={room.jobTitle}
          status={chatWorkStatusText(stage, room, "owner")}
          terms={`${formatWon(room.budget)}, 수정 ${room.revisionCount}회, 최종 마감 ${formatMonthDay(room.finalDeadline)}`}
          historyTo={OWNER_PATHS.workHistory(id)}
          action={action}
          trouble={trouble}
        />

        <p className="owner-chat__notice">
          <span aria-hidden="true">ⓘ</span>
          <span>
            채팅은 작업 질문·자료 요청용이에요.
            <br />
            작업 조건(내용·금액·마감)은 바꿀 수 없어요.
          </span>
        </p>

        <ol className="owner-chat__messages">
          {messages.map((message, i) => {
            const newDay = i === 0 || dayKey(messages[i - 1].createdAt) !== dayKey(message.createdAt);
            return (
              <li key={message.clientMessageId} className="owner-chat__item">
                {newDay && <span className="owner-chat__pill">{formatDayChip(message.createdAt)}</span>}
                {message.mine ? (
                  <div className="owner-chat__line owner-chat__line--mine">
                    <SendState message={message} onResend={resend} />
                    <MessageBody
                      message={message}
                      mine
                      onOpenExpired={openExpiredAttachment}
                      onShowPhoto={showPhoto}
                    />
                  </div>
                ) : (
                  <div className="owner-chat__partner-message">
                    <RoleAvatar role="student" size={28} />
                    <div className="owner-chat__group">
                      <span className="owner-chat__sender">{partnerName}</span>
                      <div className="owner-chat__line">
                        <MessageBody
                          message={message}
                          mine={false}
                          onOpenExpired={openExpiredAttachment}
                          onShowPhoto={showPhoto}
                        />
                        <time className="owner-chat__time">{timeOf(message.createdAt)}</time>
                      </div>
                    </div>
                  </div>
                )}
              </li>
            );
          })}
        </ol>
        <div ref={endRef} />
      </div>

      <ChatPhotoViewer
        messages={messages}
        openKey={photoKey}
        onShow={showPhoto}
        onClose={() => setPhotoKey(null)}
      />

      <ReportSheet open={reportOpen} workTitle={room.jobTitle} onClose={() => setReportOpen(false)} />
    </SubScreen>
  );
}

/** 내 말풍선 옆: 보낸 시각 · 보내는 중 · 보내지 못함과 「다시 보내기」 */
function SendState({
  message,
  onResend,
}: {
  message: ChatMessage;
  onResend: (clientMessageId: string) => void;
}) {
  if (message.status === "sending") {
    return <span className="owner-chat__time">보내는 중</span>;
  }
  if (message.status === "failed") {
    return (
      <span className="owner-chat__failed">
        <span>보내지 못했어요</span>
        {message.failureReason && <span>{message.failureReason}</span>}
        <button
          type="button"
          className="owner-chat__resend"
          onClick={() => onResend(message.clientMessageId)}
        >
          다시 보내기
        </button>
      </span>
    );
  }
  return <time className="owner-chat__time">{timeOf(message.createdAt)}</time>;
}

/** 글 · 사진 · 파일 말풍선. 열람 주소가 만료됐으면 새 주소를 받아 연다. 보낸 파일은 크기도 보인다 */
function MessageBody({
  message,
  mine,
  onOpenExpired,
  onShowPhoto,
}: {
  message: ChatMessage;
  mine: boolean;
  onOpenExpired: (message: ChatMessage) => void;
  /** 보낸 사진을 앱 안에서 크게 본다 */
  onShowPhoto: (message: ChatMessage) => void;
}) {
  if (message.type === "TEXT") {
    return (
      <span className={`owner-chat__bubble${mine ? " owner-chat__bubble--mine" : ""}`}>
        {message.content}
      </span>
    );
  }

  const name = message.attachmentName ?? (message.type === "IMAGE" ? "사진" : "파일");
  const detail = attachmentDetailText(message);
  const fileBody = (
    <>
      <span aria-hidden="true">📄</span>
      <span className="owner-chat__file-info">
        <strong>{name}</strong>
        {detail && <small>{detail}</small>}
      </span>
    </>
  );

  // 보내는 중 · 보내지 못한 첨부: 사진은 고른 파일 미리보기, 파일은 이름과 크기
  if (message.status !== "sent") {
    return message.type === "IMAGE" && message.content ? (
      <span className="owner-chat__image">
        <img src={message.content} alt={name} />
      </span>
    ) : (
      <span className="owner-chat__file">{fileBody}</span>
    );
  }

  if (!message.content) {
    return (
      <span className="owner-chat__file">
        <span aria-hidden="true">{message.type === "IMAGE" ? "🖼️" : "📄"}</span>
        <span className="owner-chat__file-info">
          <strong>{name}</strong>
          <small>열 수 없는 파일이에요</small>
        </span>
      </span>
    );
  }

  const handleClick = (e: MouseEvent) => {
    if (!isAttachmentExpired(message)) return;
    e.preventDefault();
    onOpenExpired(message);
  };

  if (message.type === "IMAGE") {
    return (
      <a
        className="owner-chat__image"
        href={message.content}
        target="_blank"
        rel="noopener noreferrer"
        onClick={(e) => {
          e.preventDefault();
          onShowPhoto(message);
        }}
      >
        <img src={message.content} alt={name} />
      </a>
    );
  }
  return (
    <a
      className="owner-chat__file"
      href={message.content}
      target="_blank"
      rel="noopener noreferrer"
      onClick={handleClick}
    >
      {fileBody}
    </a>
  );
}

export default OwnerChatRoomPage;
