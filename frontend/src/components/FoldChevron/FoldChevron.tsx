import MaskIcon from "../MaskIcon/MaskIcon";
import "./FoldChevron.css";

interface FoldChevronProps {
  /** 펼쳐져 있으면 위(접기), 아니면 아래(펼치기) */
  open: boolean;
}

/** 「펼치기」 · 「접기」 글자 뒤 화살표. 글자색으로 칠하고, 펼치고 접을 때 아래 ↔ 위로 돈다 */
function FoldChevron({ open }: FoldChevronProps) {
  return <MaskIcon name="iconChevronRight14" size={14} className={`fold-chevron${open ? " fold-chevron--open" : ""}`} />;
}

export default FoldChevron;
