import type { Role } from "../../types/role";
import RoleAvatar from "../RoleAvatar/RoleAvatar";
import "./ChatRow.css";

interface ChatRowProps {
  /** 내 역할. 안 읽은 수 색을 고른다 */
  tone: Role;
  /** 상대 역할. 프로필 사진을 고른다 */
  partnerRole: Role;
  /** 상대가 올린 프로필 사진. 없으면 역할 아이콘 */
  partnerPhoto?: string | null;
  /** 「김광운 학생」 */
  name: string;
  workTitle: string;
  /** 굵은 진행 상태 (예: 초안을 확인해 주세요) */
  status: string;
  lastMessage: string;
  /** 「어제」 · 「9월 12일」 */
  time: string;
  unreadCount: number;
  onClick: () => void;
}

/** 채팅 목록 한 줄. 프로필 · 이름과 작업 · 진행 상태 · 마지막 메시지 · 시간과 안 읽은 수 */
function ChatRow({
  tone,
  partnerRole,
  partnerPhoto,
  name,
  workTitle,
  status,
  lastMessage,
  time,
  unreadCount,
  onClick,
}: ChatRowProps) {
  return (
    <button type="button" className={`chat-row chat-row--${tone}`} onClick={onClick}>
      <RoleAvatar role={partnerRole} size={48} src={partnerPhoto} />
      <span className="chat-row__content">
        <span className="chat-row__head">
          <span className="chat-row__name">{name}</span>
          <span className="chat-row__work">{workTitle}</span>
        </span>
        <span className="chat-row__status">{status}</span>
        <span className="chat-row__message">{lastMessage}</span>
      </span>
      <span className="chat-row__side">
        <span className="chat-row__time">{time}</span>
        {unreadCount > 0 && (
          <span className="chat-row__unread" aria-label={`안 읽은 메시지 ${unreadCount}개`}>
            {unreadCount}
          </span>
        )}
      </span>
    </button>
  );
}

export default ChatRow;
