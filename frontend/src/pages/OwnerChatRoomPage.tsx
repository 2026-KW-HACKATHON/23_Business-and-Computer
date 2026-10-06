import { useEffect, useRef, useState } from "react";
import type { FormEvent, MouseEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  LoadNotice,
  ReportSheet,
  RoleAvatar,
  SubScreen,
  TextButton,
  WorkKindIcon,
} from "../components";
import {
  canCancelChatWork,
  canReportChatWork,
  chatPlanOf,
  chatSummaryText,
  isAttachmentExpired,
  useChatRoom,
} from "../features/chat";
import type { ChatMessage } from "../features/chat";
import { OWNER_PATHS, WorkPlanSheet } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatDayChip, formatMonthDay } from "../lib/date";
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
  const { load, messages, reload, send, resend, openExpiredAttachment } = useChatRoom(
    roomId,
    OWNER_PATHS.chats,
  );
  const [draft, setDraft] = useState("");
  const [planOpen, setPlanOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  // 처음 들어올 때와 메시지가 늘 때 맨 아래로
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

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
  const plan = chatPlanOf(room);
  const summary = chatSummaryText(room, "owner");
  const canCancel = canCancelChatWork(room);
  const canReport = canReportChatWork(room);
  const partnerName = `${room.counterpartName} 학생`;

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const text = draft.trim();
    if (!text) return;
    send(text);
    setDraft("");
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
        <div className="owner-chat__work">
          <div className="owner-chat__work-head">
            <WorkKindIcon kind="request" size={20} />
            <strong className="owner-chat__work-title">{room.jobTitle}</strong>
            {plan && <TextButton onClick={() => setPlanOpen(true)}>작업계획서 보기</TextButton>}
          </div>
          {summary && <p className="owner-chat__work-progress">{summary}</p>}
          <p className="owner-chat__work-terms">
            {`${formatWon(room.budget)}, 수정 ${room.revisionCount}회, 최종 마감 ${formatMonthDay(room.finalDeadline)}`}
          </p>
          {/* 작업 상태를 알 때만: 취소는 확인할 결과물이 없는 작업 중에만, 신고는 작업 중에만 */}
          {(canCancel || canReport) && (
            <div className="owner-chat__work-actions">
              {canCancel && (
                <TextButton
                  showChevron={false}
                  onClick={() => navigate(OWNER_PATHS.workCancel(String(room.jobId)))}
                >
                  작업 취소
                </TextButton>
              )}
              {canReport && (
                <TextButton showChevron={false} onClick={() => setReportOpen(true)}>
                  문제 신고
                </TextButton>
              )}
            </div>
          )}
        </div>

        <p className="owner-chat__notice">
          <span aria-hidden="true">ⓘ</span>
          채팅은 작업 질문·자료 요청용이에요. 내용·금액·마감 같은 작업 조건은 채팅으로 바뀌지 않아요.
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
                    <MessageBody message={message} mine onOpenExpired={openExpiredAttachment} />
                  </div>
                ) : (
                  <div className="owner-chat__partner-message">
                    <RoleAvatar role="student" size={28} />
                    <div className="owner-chat__group">
                      <span className="owner-chat__sender">{partnerName}</span>
                      <div className="owner-chat__line">
                        <MessageBody message={message} mine={false} onOpenExpired={openExpiredAttachment} />
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

      <WorkPlanSheet
        work={
          planOpen && plan
            ? {
                title: room.jobTitle,
                student: { name: room.counterpartName },
                plan,
                budget: room.budget,
                draftDue: room.draftDeadline,
                finalDue: room.finalDeadline,
                revisionLimit: room.revisionCount,
              }
            : undefined
        }
        onClose={() => setPlanOpen(false)}
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

/** 글 · 사진 · 파일 말풍선. 열람 주소가 만료됐으면 새 주소를 받아 연다 */
function MessageBody({
  message,
  mine,
  onOpenExpired,
}: {
  message: ChatMessage;
  mine: boolean;
  onOpenExpired: (message: ChatMessage) => void;
}) {
  if (message.type === "TEXT") {
    return (
      <span className={`owner-chat__bubble${mine ? " owner-chat__bubble--mine" : ""}`}>
        {message.content}
      </span>
    );
  }

  const name = message.attachmentName ?? (message.type === "IMAGE" ? "사진" : "파일");
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
        onClick={handleClick}
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
      <span aria-hidden="true">📄</span>
      <span className="owner-chat__file-info">
        <strong>{name}</strong>
      </span>
    </a>
  );
}

export default OwnerChatRoomPage;
