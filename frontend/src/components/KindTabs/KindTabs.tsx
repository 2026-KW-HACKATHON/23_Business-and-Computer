import type { ImageName } from "../AppImage/images";
import AppImage from "../AppImage/AppImage";
import "./KindTabs.css";

export type CardKind = "all" | "proposal" | "request";

const KINDS: { kind: CardKind; label: string; icon?: ImageName }[] = [
  { kind: "all", label: "전체" },
  { kind: "proposal", label: "제안", icon: "iconCardProposal" },
  { kind: "request", label: "의뢰", icon: "iconCardRequest" },
];

interface KindTabsProps {
  value: CardKind;
  onChange: (kind: CardKind) => void;
}

/** 탐색 목록의 종류 선택 (전체 / 제안 / 의뢰). 고른 칸만 흰 바탕으로 떠오른다 */
function KindTabs({ value, onChange }: KindTabsProps) {
  return (
    <div className="kind-tabs" role="group" aria-label="종류">
      {KINDS.map(({ kind, label, icon }) => (
        <button
          key={kind}
          type="button"
          className={`kind-tabs__tab${kind === value ? " kind-tabs__tab--selected" : ""}`}
          aria-pressed={kind === value}
          onClick={() => onChange(kind)}
        >
          {icon && <AppImage name={icon} width={16} alt="" className="kind-tabs__icon" />}
          {label}
        </button>
      ))}
    </div>
  );
}

export default KindTabs;
