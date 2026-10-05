import type { ReactNode } from "react";
import { WorkKindIcon } from "../../../components";
import "./WorkSummary.css";

interface WorkSummaryProps {
  kind: "proposal" | "request";
  title: string;
  /** 제목 아래 회색 줄 (줄바꿈 \n 가능) */
  meta: string;
  /** 오른쪽 (예: 완료 칩) */
  right?: ReactNode;
}

/** 작업 화면 맨 위 회색 작업 요약 (아이콘 + 제목 + 가게 · 조건) */
function WorkSummary({ kind, title, meta, right }: WorkSummaryProps) {
  return (
    <div className="work-summary">
      <div className="work-summary__head">
        <WorkKindIcon kind={kind} size={22} />
        <h2 className="work-summary__title">{title}</h2>
        {right}
      </div>
      <p className="work-summary__meta">{meta}</p>
    </div>
  );
}

export default WorkSummary;
