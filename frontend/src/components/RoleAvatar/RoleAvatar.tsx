import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import "./RoleAvatar.css";

interface RoleAvatarProps {
  /** 가게(사장님) 또는 학생 */
  role: Role;
  /** 한 변 크기(px). 기본 40, 채팅 목록은 48 */
  size?: number;
}

/** 작은 프로필 사진 (40px). 흰 원 + 연노랑 테두리 안에 역할 아이콘 */
function RoleAvatar({ role, size = 40 }: RoleAvatarProps) {
  return (
    <span className="role-avatar" style={{ width: size, height: size }}>
      <AppImage name={role === "owner" ? "roleOwner" : "roleStudent"} width={size - 2} />
    </span>
  );
}

export default RoleAvatar;
