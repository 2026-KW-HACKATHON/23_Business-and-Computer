import type { StoreConcern } from "../types";
import "./StoreConcern.css";

interface StoreConcernLineProps {
  concern: StoreConcern | null | undefined;
}

/** 가게 목록 한 줄 아래 「고민」 꼬리표와 고민 한 줄. 고민이 없으면 「고민 없음」 */
function StoreConcernLine({ concern }: StoreConcernLineProps) {
  if (!concern) {
    return <span className="store-concern-line store-concern-line--none">고민 없음</span>;
  }
  return (
    <span className="store-concern-line">
      <span className="store-concern-line__label">고민</span>
      <span className="store-concern-line__title">{concern.title}</span>
    </span>
  );
}

export default StoreConcernLine;
