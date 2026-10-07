import { RoleAvatar, TextButton } from "../../../components";
import { studentTitle } from "../../../lib/korean";
import "./StudentBox.css";

interface StudentBoxProps {
  /** 학생 이름. 모르면 「학생」 */
  name?: string;
  /** 이름 아래 줄 (학번 · 학과, 기록). 빈 값은 뺀다 */
  lines: (string | undefined)[];
  onProfile: () => void;
}

/** 받은 제안 · 보낸 의뢰 상세의 학생 상자. 「프로필 보기」로 학생 프로필에 간다 */
function StudentBox({ name, lines, onProfile }: StudentBoxProps) {
  const sub = lines.filter(Boolean).join("\n");
  return (
    <div className="student-box">
      <RoleAvatar role="student" />
      <div className="student-box__info">
        <strong className="student-box__name">{name ? studentTitle(name) : "학생"}</strong>
        {sub && <span className="student-box__sub">{sub}</span>}
      </div>
      <TextButton onClick={onProfile}>프로필 보기</TextButton>
    </div>
  );
}

export default StudentBox;
