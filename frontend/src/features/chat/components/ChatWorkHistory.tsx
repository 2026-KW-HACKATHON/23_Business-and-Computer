import { Link } from "react-router-dom";
import { FlowBar, WorkKindIcon } from "../../../components";
import type { FlowStep } from "../../../components";
import type { Role } from "../../../types/role";
import type { WorkKind } from "../../../types/workKind";
import "./ChatWorkHistory.css";

/** 서류 한 줄. to 가 없으면 열 수 없는 줄 (회색) */
export interface ChatWorkHistoryRow {
  label: string;
  /** 회색 설명 (날짜 없음) */
  sub: string;
  to?: string;
}

interface ChatWorkHistoryProps {
  role: Role;
  kind: WorkKind;
  title: string;
  /** 작업 중 · 완료 · 성사되지 않음 */
  badge: string;
  /** 상대 이름 · 작업비 · 수정 횟수 (줄바꿈 가능) */
  meta: string;
  /** 없으면 흐름 막대를 숨긴다 (성사되지 않은 작업) */
  steps?: FlowStep[];
  rows: ChatWorkHistoryRow[];
}

/**
 * 피그마 「작업 이력」 (ADR 0045). 작업 이름과 흐름 막대 아래에 그 작업에 쌓인 서류를 생긴 순서대로
 * 보인다 (날짜 없음). 줄을 누르면 그 서류 화면으로 간다
 */
function ChatWorkHistory({ role, kind, title, badge, meta, steps, rows }: ChatWorkHistoryProps) {
  return (
    <div className="chat-work-history">
      <div className="chat-work-history__work">
        <div className="chat-work-history__head">
          <WorkKindIcon kind={kind} size={22} />
          <h2 className="chat-work-history__title">{title}</h2>
          <span className="chat-work-history__badge">{badge}</span>
        </div>
        <p className="chat-work-history__meta">{meta}</p>
      </div>

      {steps && <FlowBar tone={role} steps={steps} />}

      <ul className="chat-work-history__list">
        {rows.map((row) => {
          const body = (
            <>
              <span className="chat-work-history__icon" aria-hidden="true">
                📄
              </span>
              <span className="chat-work-history__text">
                <strong>{row.label}</strong>
                <small>{row.sub}</small>
              </span>
              {row.to && (
                <span className="chat-work-history__chevron" aria-hidden="true">
                  ›
                </span>
              )}
            </>
          );
          return (
            <li key={row.label}>
              {row.to ? (
                <Link className="chat-work-history__row" to={row.to}>
                  {body}
                </Link>
              ) : (
                <div className="chat-work-history__row chat-work-history__row--closed">{body}</div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export default ChatWorkHistory;
