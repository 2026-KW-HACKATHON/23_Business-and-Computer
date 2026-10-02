import type { WorkKind } from "../../types/workKind";
import AppImage from "../AppImage/AppImage";
import "./WorkKindIcon.css";

interface WorkKindIconProps {
  kind: WorkKind;
  /** 한 변 크기(px) */
  size?: number;
}

/** 제안·의뢰 카드 제목 앞 종류 아이콘. 의뢰 = 클립보드, 제안 = 전구 */
function WorkKindIcon({ kind, size = 24 }: WorkKindIconProps) {
  return (
    <AppImage
      name={kind === "proposal" ? "iconCardProposal" : "iconCardRequest"}
      alt={kind === "proposal" ? "제안" : "의뢰"}
      width={size}
      height={size}
      className="work-kind-icon"
    />
  );
}

export default WorkKindIcon;
