import AppImage from "../AppImage/AppImage";
import "./EmpathyCount.css";

interface EmpathyCountProps {
  count: number;
  /** 내가 공감했으면 빨간 하트 + 검정 숫자 */
  empathized?: boolean;
}

/** 제안 카드·피드의 공감(좋아요) 수 */
function EmpathyCount({ count, empathized = false }: EmpathyCountProps) {
  return (
    <span
      className={`empathy-count${empathized ? " empathy-count--on" : ""}`}
      aria-label={`공감 ${count}`}
    >
      <AppImage name={empathized ? "iconHeart" : "iconHeartEmpty"} alt="" />
      <span>{count}</span>
    </span>
  );
}

export default EmpathyCount;
