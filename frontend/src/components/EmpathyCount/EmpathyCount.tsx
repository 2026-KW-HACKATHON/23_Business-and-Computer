import AppImage from "../AppImage/AppImage";
import "./EmpathyCount.css";

interface EmpathyCountProps {
  count: number;
  /** 내가 공감했으면 빨간 하트 + 검정 숫자 */
  empathized?: boolean;
  /** 넣으면 눌러서 공감을 켜고 끄는 버튼이 된다 */
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
      className={className}
      onClick={onToggle}
      aria-pressed={empathized}
      aria-label={`공감 ${count}`}
    >
      {content}
    </button>
  );
}

export default EmpathyCount;
