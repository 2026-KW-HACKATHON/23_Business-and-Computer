import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import "../Button/Button.css";
import "./RoleCard.css";

interface RoleCardProps {
  role: Role;
  /** 역할 이름 아래 한 줄 설명 (예: 제안 받기·의뢰하기) */
  description: string;
  /** 카드 아래 버튼 글자 (예: 시작하기 ›) */
  actionLabel: string;
  onSelect: () => void;
}

const ROLE_INFO = {
  owner: { name: "사장님", character: "characterOwner" },
  student: { name: "대학생", character: "characterStudent" },
} as const;

/** 역할 선택 카드. 카드 어디를 눌러도 같은 곳으로 간다 */
function RoleCard({ role, description, actionLabel, onSelect }: RoleCardProps) {
  const { name, character } = ROLE_INFO[role];

  return (
    <button type="button" className="role-card" onClick={onSelect}>
      <span className="role-card__character">
        <AppImage name={character} width={108.8} alt="" priority />
      </span>
      <strong className="role-card__name">{name}</strong>
      <span className="role-card__description">{description}</span>
      <span className={`button button--${role} button--full role-card__action`}>{actionLabel}</span>
    </button>
  );
}

export default RoleCard;
