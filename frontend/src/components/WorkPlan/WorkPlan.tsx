import { useState } from "react";
import TextButton from "../TextButton/TextButton";
import "./WorkPlan.css";

interface WorkPlanProps {
  /** 줄마다 한 문단. 「· 방법: …」처럼 쓴다 */
  text: string;
  /** 지원자 카드 안: 「작업계획서」 이름 + 2줄 미리보기 + 「전체 보기 / 접기」 */
  collapsible?: boolean;
}

/** 학생 작업계획서 (회색 상자) */
function WorkPlan({ text, collapsible = false }: WorkPlanProps) {
  const [open, setOpen] = useState(false);

  if (!collapsible) {
    return (
      <div className="work-plan">
        {text.split("\n").map((line, i) => (
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
      <p className={`work-plan__text${open ? "" : " work-plan__text--clamped"}`}>{text}</p>
      <TextButton className="work-plan__toggle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        {open ? "접기" : "전체 보기"}
      </TextButton>
    </div>
  );
}

export default WorkPlan;
