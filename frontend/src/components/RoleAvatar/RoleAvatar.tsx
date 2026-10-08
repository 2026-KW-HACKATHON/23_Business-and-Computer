import { useState } from "react";
import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import "./RoleAvatar.css";

interface RoleAvatarProps {
  /** 가게(사장님) 또는 학생 */
  role: Role;
  /** 한 변 크기(px). 기본 40, 채팅 목록은 48 */
  size?: number;
  /** 그 사람이 올린 프로필 사진(서버 https 주소). 없거나 불러오지 못하면 역할 아이콘 */
  src?: string | null;
}

/** 작은 프로필 사진 (40px). 올린 사진이 있으면 원 안에 채우고, 없으면 흰 원 + 연노랑 테두리 안에 역할 아이콘 */
function RoleAvatar({ role, size = 40, src }: RoleAvatarProps) {
  // 불러오지 못한 주소. 다른 주소로 바뀌면 다시 시도한다
  const [failed, setFailed] = useState<string>();
  const photo = src && src.startsWith("https://") && src !== failed ? src : undefined;

  return (
    <span className={`role-avatar${photo ? " role-avatar--photo" : ""}`} style={{ width: size, height: size }}>
      {photo ? (
        <img className="role-avatar__photo" src={photo} alt="" loading="lazy" onError={() => setFailed(photo)} />
      ) : (
        <AppImage name={role === "owner" ? "roleOwner" : "roleStudent"} width={size - 2} />
      )}
    </span>
  );
}

export default RoleAvatar;
