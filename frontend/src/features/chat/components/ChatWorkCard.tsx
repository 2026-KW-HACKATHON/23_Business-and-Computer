import { useId, useState } from "react";
import { Link } from "react-router-dom";
import { AppImage, Button, TextButton, WorkKindIcon } from "../../../components";
import type { Role } from "../../../types/role";
import type { WorkKind } from "../../../types/workKind";
import "./ChatWorkCard.css";

/** 「문제가 있나요?」를 펼치면 보이는 글자 버튼 하나 (작업 취소 · 문제 신고) */
export interface ChatWorkTroubleItem {
  label: string;
  onSelect: () => void;
}

interface ChatWorkCardProps {
  role: Role;
  kind: WorkKind;
  title: string;
  /** 굵은 진행 상태 (지금 단계). 없으면 그 줄을 숨긴다 */
  status?: string;
  /** 작업비 · 수정 횟수 · 최종 마감 */
  terms: string;
  /** 제목 줄 「이력 상세보기 ›」 → 작업 이력 */
  historyTo: string;
  /** 지금 할 일. 카드 아래 버튼 하나 */
  action?: { label: string; onClick: () => void };
  /** 비면 「문제가 있나요?」 줄이 없다 */
  trouble: ChatWorkTroubleItem[];
}

/**
 * 피그마 「채팅 작업 카드」 (ADR 0045). 지금 단계는 굵은 글자로, 서류는 제목 줄 「이력 상세보기 ›」로
 * 여는 작업 이력에서 본다. 지금 할 일 버튼 하나, 맨 아래 「문제가 있나요?」를 누르면 작업 취소 · 문제 신고
 * 글자 버튼이 펼쳐진다 (내 활동 카드의 「문제가 있나요?」 줄과 같은 모양)
 */
function ChatWorkCard({ role, kind, title, status, terms, historyTo, action, trouble }: ChatWorkCardProps) {
  const [troubleOpen, setTroubleOpen] = useState(false);
  const troubleId = useId();

  return (
    <div className={`chat-work-card chat-work-card--${role}`}>
      <div className="chat-work-card__head">
        <WorkKindIcon kind={kind} size={20} />
        <strong className="chat-work-card__title">{title}</strong>
        <Link className="chat-work-card__history" to={historyTo}>
          <span>이력 상세보기</span>
          <AppImage name="iconChevronRight14" />
        </Link>
      </div>
      {status && <p className="chat-work-card__status">{status}</p>}
      <p className="chat-work-card__terms">{terms}</p>
      {action && (
        <Button tone={role} size="medium" fullWidth onClick={action.onClick}>
          {action.label}
        </Button>
      )}
      {trouble.length > 0 && (
        <div className="chat-work-card__trouble">
          <button
            type="button"
            className="chat-work-card__trouble-toggle"
            aria-expanded={troubleOpen}
            aria-controls={troubleId}
            onClick={() => setTroubleOpen((open) => !open)}
          >
            문제가 있나요?
            <AppImage name="iconChevronRight14" className="chat-work-card__trouble-arrow" />
          </button>
          {troubleOpen && (
            <div id={troubleId} className="chat-work-card__trouble-items">
              {trouble.map((item) => (
                <TextButton key={item.label} onClick={item.onSelect}>
                  {item.label}
                </TextButton>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default ChatWorkCard;
