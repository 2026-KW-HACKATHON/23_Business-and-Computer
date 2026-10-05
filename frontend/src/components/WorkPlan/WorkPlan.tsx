import { useState } from "react";
import type { ApplicationPlan, WorkPlanContent } from "../../types/workPlan";
import TextButton from "../TextButton/TextButton";
import "./WorkPlan.css";

interface WorkPlanProps {
  /** 의뢰 지원서(한 줄 요약 · 작업계획서 · 결과물) 또는 제안서 작업계획서 글 (「· 방법: …」 줄마다 한 문단) */
  plan: WorkPlanContent;
  /** 지원자 카드 안: 접으면 한 줄 요약과 작업계획서 2줄만, 「전체 보기 / 접기」 */
  collapsible?: boolean;
}

const SECTIONS: { key: keyof ApplicationPlan; label: string }[] = [
  { key: "summary", label: "한 줄 요약" },
  { key: "method", label: "작업계획서" },
  { key: "deliverable", label: "결과물" },
];

/** 학생 작업계획서 (회색 상자) */
function WorkPlan({ plan, collapsible = false }: WorkPlanProps) {
  const [open, setOpen] = useState(false);
  const toggle = collapsible && (
    <TextButton className="work-plan__toggle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
      {open ? "접기" : "전체 보기"}
    </TextButton>
  );

  if (typeof plan === "string") {
    if (!collapsible) {
      return (
        <div className="work-plan">
          {plan.split("\n").map((line, i) => (
            <p key={i} className="work-plan__line">
              {line}
            </p>
          ))}
        </div>
      );
    }
    return (
      <div className="work-plan work-plan--preview">
        <p className="work-plan__label">작업계획서</p>
        <p className={`work-plan__text${open ? "" : " work-plan__text--clamped"}`}>{plan}</p>
        {toggle}
      </div>
    );
  }

  const folded = collapsible && !open;
  const sections = folded ? SECTIONS.filter(({ key }) => key !== "deliverable") : SECTIONS;
  return (
    <div className={`work-plan work-plan--sections${collapsible ? " work-plan--preview" : ""}`}>
      {sections.map(({ key, label }) => (
        <div key={key} className="work-plan__section">
          <p className="work-plan__label">{label}</p>
          <p
            className={`work-plan__text${folded && key === "method" ? " work-plan__text--clamped" : ""}`}
          >
            {plan[key]}
          </p>
        </div>
      ))}
      {toggle}
    </div>
  );
}

export default WorkPlan;
