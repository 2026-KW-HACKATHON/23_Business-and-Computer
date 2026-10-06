import type { ReactNode } from "react";
import { RoleAvatar } from "../../../components";
import "./StoreBox.css";

interface StoreBoxProps {
  name: string;
  address?: string;
  /** 주소 아래 작은 회색 줄 (예: 사장님이 제안을 받아들였어요) */
  note?: string;
  /** 오른쪽 (예: 인증 칩) */
  right?: ReactNode;
}

/** 테두리 상자 안 가게 사진 + 이름 · 주소 */
function StoreBox({ name, address, note, right }: StoreBoxProps) {
  return (
    <div className="store-box">
      <RoleAvatar role="owner" />
      <span className="store-box__info">
        <strong>{name}</strong>
        {address && <span>{address}</span>}
        {note && <small>{note}</small>}
      </span>
      {right}
    </div>
  );
}

export default StoreBox;
