import { useEffect, useRef, useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  ReportSheet,
  RoleAvatar,
  SubScreen,
  TextButton,
  WorkKindIcon,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  WorkPlanSheet,
  useOwnerChatThread,
  useOwnerWork,
  workChatSummary,
} from "../features/owner";
import type { ChatMessage } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatDayChip, formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerChatRoomPage.css";

const timeOf = (iso: string) => {
  const date = new Date(iso);
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
};

const dayKey = (iso: string) => new Date(iso).toDateString();

const fileSize = (bytes: number) => `${(bytes / 1024 / 1024).toFixed(1)}MB`;

/** 보낸 메시지 (id · 시각은 보낼 때 붙인다) */
type Outgoing =
  | { type: "text"; from: "me"; text: string }
  | { type: "file"; from: "me"; name: string; detail: string };

/**
 * 피그마 「채팅방 (사장님)」. 작업 하나에 채팅방 하나.
 * 백엔드 연동 전까지 보낸 메시지·파일은 이 화면 안에서만 보인다.
 */
function OwnerChatRoomPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.chats);
  const work = useOwnerWork(workId);
  const thread = useOwnerChatThread(workId);
  const [sent, setSent] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [planOpen, setPlanOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);
  const messages = [...(thread?.messages ?? []), ...sent];

  // 처음 들어올 때와 메시지를 보낼 때 맨 아래로
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  if (!work) return <OwnerMissing title="채팅" onBack={back} />;

  const append = (message: Outgoing) => {
    setSent((list) => [
      ...list,
      { ...message, id: `sent-${list.length}`, at: new Date().toISOString() },
    ]);
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const text = draft.trim();
    if (!text) return;
    append({ type: "text", from: "me", text });
    setDraft("");
  };

  const handleFile = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) append({ type: "file", from: "me", name: file.name, detail: fileSize(file.size) });
    e.target.value = "";
  };

  const partnerName = `${work.student.name} 학생`;

  return (
    <SubScreen
      title={
        <span className="owner-chat__partner">
          <RoleAvatar role="student" size={32} />
          <span className="owner-chat__partner-text">
            <strong>{partnerName}</strong>
            <small>{work.title}</small>
          </span>
        </span>
      }
      onBack={back}
      footer={
        <form className="owner-chat__composer" onSubmit={handleSubmit}>
          <label className="owner-chat__attach" aria-label="파일 보내기">
            +
            <input type="file" onChange={handleFile} />
          </label>
          <input
            className="owner-chat__input"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
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
            <WorkKindIcon kind={work.kind} size={20} />
            <strong className="owner-chat__work-title">{work.title}</strong>
            <TextButton onClick={() => setPlanOpen(true)}>작업계획서 보기</TextButton>
          </div>
          <p className="owner-chat__work-progress">{workChatSummary(work)}</p>
          <p className="owner-chat__work-terms">
            {`${formatWon(work.budget)}, 수정 ${work.revisionLimit}회, 최종 마감 ${formatMonthDay(work.finalDue)}`}
          </p>
          {/* 취소는 작업 중에만, 신고는 끝나기 전까지만 */}
          {(work.status === "inProgress" || work.status === "submitted") && (
            <div className="owner-chat__work-actions">
              {work.status === "inProgress" && (
                <TextButton
                  showChevron={false}
                  onClick={() => navigate(OWNER_PATHS.workCancel(work.id))}
                >
                  작업 취소
                </TextButton>
              )}
              <TextButton showChevron={false} onClick={() => setReportOpen(true)}>
                문제 신고
              </TextButton>
            </div>
          )}
        </div>

        <p className="owner-chat__notice">
          <span aria-hidden="true">ⓘ</span>
          채팅은 작업 질문·자료 요청용이에요. 내용·금액·마감 같은 작업 조건은 채팅으로 바뀌지 않아요.
        </p>

        <ol className="owner-chat__messages">
          {messages.map((message, i) => {
            const newDay = i === 0 || dayKey(messages[i - 1].at) !== dayKey(message.at);
            return (
              <li key={message.id} className="owner-chat__item">
                {newDay && <span className="owner-chat__pill">{formatDayChip(message.at)}</span>}
                {message.type === "system" ? (
                  <span className="owner-chat__pill">{message.text}</span>
                ) : message.from === "partner" ? (
                  <div className="owner-chat__partner-message">
                    <RoleAvatar role="student" size={28} />
                    <div className="owner-chat__group">
                      <span className="owner-chat__sender">{partnerName}</span>
                      <div className="owner-chat__line">
                        <MessageBody message={message} mine={false} />
                        <time className="owner-chat__time">{timeOf(message.at)}</time>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="owner-chat__line owner-chat__line--mine">
                    <time className="owner-chat__time">{timeOf(message.at)}</time>
                    <MessageBody message={message} mine />
                  </div>
                )}
              </li>
            );
          })}
        </ol>
        <div ref={endRef} />
      </div>

      <WorkPlanSheet work={planOpen ? work : undefined} onClose={() => setPlanOpen(false)} />
      <ReportSheet open={reportOpen} workTitle={work.title} onClose={() => setReportOpen(false)} />
    </SubScreen>
  );
}

/** 글 말풍선 또는 파일 말풍선 */
function MessageBody({ message, mine }: { message: ChatMessage; mine: boolean }) {
  if (message.type === "file") {
    return (
      <span className="owner-chat__file">
        <span aria-hidden="true">📄</span>
        <span className="owner-chat__file-info">
          <strong>{message.name}</strong>
          <small>{message.detail}</small>
        </span>
      </span>
    );
  }
  if (message.type === "text") {
    return (
      <span className={`owner-chat__bubble${mine ? " owner-chat__bubble--mine" : ""}`}>
        {message.text}
      </span>
    );
  }
  return null;
}

export default OwnerChatRoomPage;
