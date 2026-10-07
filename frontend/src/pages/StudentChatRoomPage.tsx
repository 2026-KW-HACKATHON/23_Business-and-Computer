import { useState } from "react";
import type { ChangeEvent, FormEvent, MouseEvent } from "react";
import { useParams } from "react-router-dom";
import {
  LoadNotice,
  ReportSheet,
  RoleAvatar,
  SubScreen,
  TextButton,
  WorkKindIcon,
} from "../components";
import {
  canReportChatWork,
  chatPlanOf,
  chatSummaryText,
  attachmentDetailText,
  isAttachmentExpired,
  useChatRoom,
  useScrollToLatest,
} from "../features/chat";
import type { ChatMessage } from "../features/chat";
import { MyPlanSheet, STUDENT_PATHS, useProposalJobIds } from "../features/student";
import { useBack } from "../hooks/useBack";
import { ATTACHMENT_ACCEPT } from "../lib/attachmentFormats";
import { formatDayChip, formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentChatRoomPage.css";

const timeOf = (iso: string) => {
  const date = new Date(iso);
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
};

const dayKey = (iso: string) => new Date(iso).toDateString();

/** 피그마 「채팅방 (학생)」. 작업 하나에 채팅방 하나. 방이 바뀌면 새로 그린다 */
function StudentChatRoomPage() {
  const { roomId = "" } = useParams();
  return <StudentChatRoom key={roomId} roomId={roomId} />;
}

function StudentChatRoom({ roomId }: { roomId: string }) {
  const back = useBack(STUDENT_PATHS.chats);
  const { load, messages, reload, send, sendAttachment, resend, openExpiredAttachment } = useChatRoom(
    roomId,
    STUDENT_PATHS.chats,
  );
  const [draft, setDraft] = useState("");
  const [planOpen, setPlanOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  // 처음 들어올 때, 맨 아래 근처에서 새 메시지를 받을 때, 내가 보낼 때 맨 아래로
  const endRef = useScrollToLatest(messages);
  // 받은 · 보낸 제안의 의뢰면 제안에서 시작한 작업
  const proposalJobIds = useProposalJobIds();

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
  const summary = chatSummaryText(room, "student");
  const canReport = canReportChatWork(room);
  const partnerName = `${room.counterpartName} 사장님`;

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
        <span className="student-chat__partner">
          <RoleAvatar role="owner" size={32} />
          <span className="student-chat__partner-text">
            <strong>{partnerName}</strong>
            <small>{room.jobTitle}</small>
          </span>
        </span>
      }
      onBack={back}
      footer={
        <form className="student-chat__composer" onSubmit={handleSubmit}>
          <label className="student-chat__attach">
            <span aria-hidden="true">+</span>
            <input type="file" accept={ATTACHMENT_ACCEPT} aria-label="사진·파일 보내기" onChange={handleFile} />
          </label>
          <input
            className="student-chat__input"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            maxLength={5000}
            placeholder="작업 질문이나 자료 요청을 적어 주세요"
            aria-label="메시지"
          />
          <button type="submit" className="student-chat__send" aria-label="보내기">
            →
          </button>
        </form>
      }
    >
      <div className="student-chat">
        <div className="student-chat__work">
          <div className="student-chat__work-head">
            <WorkKindIcon kind={proposalJobIds.has(room.jobId) ? "proposal" : "request"} size={20} />
            <strong className="student-chat__work-title">{room.jobTitle}</strong>
            {plan && <TextButton onClick={() => setPlanOpen(true)}>작업계획서 보기</TextButton>}
          </div>
          {summary && <p className="student-chat__work-progress">{summary}</p>}
          <p className="student-chat__work-terms">
            {`${formatWon(room.budget)}, 수정 ${room.revisionCount}회, 최종 마감 ${formatMonthDay(room.finalDeadline)}`}
          </p>
          {/* 신고는 작업 상태를 알 때 작업 중에만. 작업 취소는 사장님만 할 수 있다 */}
          {canReport && (
            <div className="student-chat__work-actions">
              <TextButton showChevron={false} onClick={() => setReportOpen(true)}>
                문제 신고
              </TextButton>
            </div>
          )}
        </div>

        <p className="student-chat__notice">
          <span aria-hidden="true">ⓘ</span>
          채팅은 작업 질문·자료 요청용이에요. 내용·금액·마감 같은 작업 조건은 채팅으로 바뀌지 않아요.
        </p>

        <ol className="student-chat__messages">
          {messages.map((message, i) => {
            const newDay = i === 0 || dayKey(messages[i - 1].createdAt) !== dayKey(message.createdAt);
            return (
              <li key={message.clientMessageId} className="student-chat__item">
                {newDay && <span className="student-chat__pill">{formatDayChip(message.createdAt)}</span>}
                {message.mine ? (
                  <div className="student-chat__line student-chat__line--mine">
                    <SendState message={message} onResend={resend} />
                    <MessageBody message={message} mine onOpenExpired={openExpiredAttachment} />
                  </div>
                ) : (
                  <div className="student-chat__partner-message">
                    <RoleAvatar role="owner" size={28} />
                    <div className="student-chat__group">
                      <span className="student-chat__sender">{partnerName}</span>
                      <div className="student-chat__line">
                        <MessageBody message={message} mine={false} onOpenExpired={openExpiredAttachment} />
                        <time className="student-chat__time">{timeOf(message.createdAt)}</time>
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

      <MyPlanSheet
        work={
          planOpen && plan
            ? {
                title: room.jobTitle,
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
      <ReportSheet
        tone="student"
        open={reportOpen}
        workTitle={room.jobTitle}
        onClose={() => setReportOpen(false)}
      />
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
    return <span className="student-chat__time">보내는 중</span>;
  }
  if (message.status === "failed") {
    return (
      <span className="student-chat__failed">
        <span>보내지 못했어요</span>
        {message.failureReason && <span>{message.failureReason}</span>}
        <button
          type="button"
          className="student-chat__resend"
          onClick={() => onResend(message.clientMessageId)}
        >
          다시 보내기
        </button>
      </span>
    );
  }
  return <time className="student-chat__time">{timeOf(message.createdAt)}</time>;
}

/** 글 · 사진 · 파일 말풍선. 열람 주소가 만료됐으면 새 주소를 받아 연다. 보낸 파일은 크기도 보인다 */
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
      <span className={`student-chat__bubble${mine ? " student-chat__bubble--mine" : ""}`}>
        {message.content}
      </span>
    );
  }

  const name = message.attachmentName ?? (message.type === "IMAGE" ? "사진" : "파일");
  const detail = attachmentDetailText(message);
  const fileBody = (
    <>
      <span aria-hidden="true">📄</span>
      <span className="student-chat__file-info">
        <strong>{name}</strong>
        {detail && <small>{detail}</small>}
      </span>
    </>
  );

  // 보내는 중 · 보내지 못한 첨부: 사진은 고른 파일 미리보기, 파일은 이름과 크기
  if (message.status !== "sent") {
    return message.type === "IMAGE" && message.content ? (
      <span className="student-chat__image">
        <img src={message.content} alt={name} />
      </span>
    ) : (
      <span className="student-chat__file">{fileBody}</span>
    );
  }

  if (!message.content) {
    return (
      <span className="student-chat__file">
        <span aria-hidden="true">{message.type === "IMAGE" ? "🖼️" : "📄"}</span>
        <span className="student-chat__file-info">
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
        className="student-chat__image"
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
      className="student-chat__file"
      href={message.content}
      target="_blank"
      rel="noopener noreferrer"
      onClick={handleClick}
    >
      {fileBody}
    </a>
  );
}

export default StudentChatRoomPage;
