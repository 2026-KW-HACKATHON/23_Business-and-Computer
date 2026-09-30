import type { Role } from "../../types/role";
import AppImage from "../AppImage/AppImage";
import "./RoleAvatar.css";

interface RoleAvatarProps {
  /** 가게(사장님) 또는 학생 */
  role: Role;
}

/** 작은 프로필 사진 (40px). 흰 원 + 연노랑 테두리 안에 역할 아이콘 */
function RoleAvatar({ role }: RoleAvatarProps) {
  return (
    <span className="role-avatar">
      <AppImage name={role === "owner" ? "roleOwner" : "roleStudent"} width={38} />
    </span>
  );
}

export default RoleAvatar;
