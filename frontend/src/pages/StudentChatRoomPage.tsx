import { useEffect, useRef, useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { useParams } from "react-router-dom";
import { ReportSheet, RoleAvatar, SubScreen, TextButton, WorkKindIcon } from "../components";
import {
  MyPlanSheet,
  STUDENT_PATHS,
  StudentMissing,
  chatStatusText,
  useStudentChatThread,
  useStudentWork,
} from "../features/student";
import type { ChatMessage } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatDayChip, formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentChatRoomPage.css";

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
 * 피그마 「채팅방 (학생)」. 작업 하나에 채팅방 하나.
 * 백엔드 연동 전까지 보낸 메시지·파일은 이 화면 안에서만 보인다.
 */
function StudentChatRoomPage() {
  const { workId = "" } = useParams();
  const back = useBack(STUDENT_PATHS.chats);
  const work = useStudentWork(workId);
  const thread = useStudentChatThread(workId);
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

  if (!work) return <StudentMissing title="채팅" onBack={back} />;

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

  const partnerName = `${work.store.name} 사장님`;

  return (
    <SubScreen
      title={
        <span className="student-chat__partner">
          <RoleAvatar role="owner" size={32} />
          <span className="student-chat__partner-text">
            <strong>{partnerName}</strong>
            <small>{work.title}</small>
          </span>
        </span>
      }
      onBack={back}
      footer={
        <form className="student-chat__composer" onSubmit={handleSubmit}>
          <label className="student-chat__attach" aria-label="파일 보내기">
            +
            <input type="file" aria-label="파일 보내기" onChange={handleFile} />
          </label>
          <input
            className="student-chat__input"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
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
            <WorkKindIcon kind={work.kind} size={20} />
            <strong className="student-chat__work-title">{work.title}</strong>
            <TextButton onClick={() => setPlanOpen(true)}>작업계획서 보기</TextButton>
          </div>
          <p className="student-chat__work-progress">{chatStatusText(work)}</p>
          <p className="student-chat__work-terms">
            {`${formatWon(work.budget)}, 수정 ${work.revisionLimit}회, 최종 마감 ${formatMonthDay(work.finalDue)}`}
          </p>
          {/* 신고는 끝나기 전까지만. 작업 취소는 사장님만 할 수 있다 */}
          {work.status !== "completed" && work.status !== "canceled" && (
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
            const newDay = i === 0 || dayKey(messages[i - 1].at) !== dayKey(message.at);
            return (
              <li key={message.id} className="student-chat__item">
                {newDay && <span className="student-chat__pill">{formatDayChip(message.at)}</span>}
                {message.type === "system" ? (
                  <span className="student-chat__pill">{message.text}</span>
                ) : message.from === "partner" ? (
                  <div className="student-chat__partner-message">
                    <RoleAvatar role="owner" size={28} />
                    <div className="student-chat__group">
                      <span className="student-chat__sender">{partnerName}</span>
                      <div className="student-chat__line">
                        <MessageBody message={message} mine={false} />
                        <time className="student-chat__time">{timeOf(message.at)}</time>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="student-chat__line student-chat__line--mine">
                    <time className="student-chat__time">{timeOf(message.at)}</time>
                    <MessageBody message={message} mine />
                  </div>
                )}
              </li>
            );
          })}
        </ol>
        <div ref={endRef} />
      </div>

      <MyPlanSheet work={planOpen ? work : undefined} onClose={() => setPlanOpen(false)} />
      <ReportSheet
        tone="student"
        open={reportOpen}
        workTitle={work.title}
        onClose={() => setReportOpen(false)}
      />
    </SubScreen>
  );
}

/** 글 말풍선 또는 파일 말풍선 */
function MessageBody({ message, mine }: { message: ChatMessage; mine: boolean }) {
  if (message.type === "file") {
    return (
      <span className="student-chat__file">
        <span aria-hidden="true">📄</span>
        <span className="student-chat__file-info">
          <strong>{message.name}</strong>
          <small>{message.detail}</small>
        </span>
      </span>
    );
  }
  if (message.type === "text") {
    return (
      <span className={`student-chat__bubble${mine ? " student-chat__bubble--mine" : ""}`}>
        {message.text}
      </span>
    );
  }
  return null;
}

export default StudentChatRoomPage;
