import type { ReactNode } from "react";
import type { WorkKind } from "../../types/workKind";
import WorkKindIcon from "../WorkKindIcon/WorkKindIcon";
import "./TaskRow.css";

interface TaskRowProps {
  kind: WorkKind;
  title: string;
  /** 제목 아래 회색 줄 (예: 김광운 학생, 초안 마감 : 9월 29일) */
  lines: string[];
  /** 회색 줄 아래 굵은 진행 상태 (예: 학생 모집 중) */
  status?: string;
  /** 넣으면 줄 전체가 버튼이 되고 오른쪽에 › 가 붙는다 */
  onClick?: () => void;
  /** 오른쪽 버튼 묶음. onClick 을 넣은 줄에서는 쓰지 않는다 */
  trailing?: ReactNode;
}

/** 홈 목록 한 줄. 종류 아이콘 + 제목 · 회색 줄 + 오른쪽 › (또는 버튼) */
function TaskRow({ kind, title, lines, status, onClick, trailing }: TaskRowProps) {
  const body = (
    <>
      <WorkKindIcon kind={kind} />
      <span className="task-row__content">
        <span className="task-row__title">{title}</span>
        <span className="task-row__lines">
          {lines.map((line, i) => (
            <span key={i}>{line}</span>
          ))}
        </span>
        {status && <span className="task-row__status">{status}</span>}
      </span>
    </>
  );

  if (onClick) {
    return (
      <button type="button" className="task-row task-row--link" onClick={onClick}>
        {body}
        <span className="task-row__chevron" aria-hidden="true">
          ›
        </span>
      </button>
    );
  }

  return (
    <div className="task-row">
      {body}
      {trailing && <div className="task-row__trailing">{trailing}</div>}
    </div>
  );
}

export default TaskRow;
