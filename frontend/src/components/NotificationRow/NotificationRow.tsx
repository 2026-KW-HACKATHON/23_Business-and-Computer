import type { ReactNode } from "react";
import type { Role } from "../../types/role";
import "./NotificationRow.css";

interface NotificationRowProps {
  /** 내 역할. 안 읽은 알림의 배경과 점 색을 고른다 */
  tone: Role;
  /** 동그라미 안 아이콘 (종류 아이콘이나 이모지) */
  icon: ReactNode;
  title: string;
  body: string;
  /** 「10분 전」 · 「어제 14:22」 */
  time: string;
  unread: boolean;
  onClick: () => void;
}

/** 알림 한 줄. 안 읽은 알림은 옅은 역할 색 바탕 + 제목 옆 점 */
function NotificationRow({ tone, icon, title, body, time, unread, onClick }: NotificationRowProps) {
  const className = [
    "notification-row",
    `notification-row--${tone}`,
    unread ? "notification-row--unread" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button type="button" className={className} onClick={onClick}>
      <span className="notification-row__icon" aria-hidden="true">
        {icon}
      </span>
      <span className="notification-row__content">
        <span className="notification-row__title">
          {title}
          {unread && <span className="notification-row__dot" aria-label="안 읽음" />}
        </span>
        <span className="notification-row__body">{body}</span>
        <span className="notification-row__time">{time}</span>
      </span>
    </button>
  );
}

export default NotificationRow;
