import AppImage from "../AppImage/AppImage";
import "./EmpathyCount.css";

interface EmpathyCountProps {
  count: number;
  /** 내가 공감했으면 빨간 하트 + 검정 숫자 */
  empathized?: boolean;
  /** 넣으면 누를 수 있다 (공감하기 · 다시 누르면 취소) */
  onToggle?: () => void;
}

/** 제안 카드·피드의 공감(좋아요) 수 */
function EmpathyCount({ count, empathized = false, onToggle }: EmpathyCountProps) {
  const className = `empathy-count${empathized ? " empathy-count--on" : ""}`;
  const content = (
    <>
      <AppImage name={empathized ? "iconHeart" : "iconHeartEmpty"} alt="" />
      <span>{count}</span>
    </>
  );

  if (!onToggle) {
    return (
      <span className={className} aria-label={`공감 ${count}`}>
        {content}
      </span>
    );
  }
  return (
    <button
      type="button"
      className={`${className} empathy-count--button`}
      aria-label={`공감 ${count}`}
      aria-pressed={empathized}
      onClick={onToggle}
    >
      {content}
    </button>
  );
}

export default EmpathyCount;
