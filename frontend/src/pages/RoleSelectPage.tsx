import { useNavigate } from "react-router-dom";
import { AppBar, AppImage, RoleCard } from "../components";
import type { Role } from "../types/role";
import "./RoleSelectPage.css";

type RoleSelectMode = "signup" | "demo";

interface RoleSelectPageProps {
  /** signup = 카카오 로그인 후 회원가입, demo = 로그인 없이 둘러보기 */
  mode: RoleSelectMode;
}

const TITLES: Record<RoleSelectMode, string> = {
  signup: "회원가입 - 역할 선택",
  demo: "둘러보기(Demo) - 역할 선택",
};

const ACTIONS: Record<RoleSelectMode, string> = {
  signup: "시작하기 ›",
  demo: "둘러보기 ›",
};

/** 화면 상태 전환표의 라우트 제안. 아직 없는 화면은 만들어지면 연결된다. */
const NEXT_PATHS: Record<RoleSelectMode, Record<Role, string>> = {
  signup: { owner: "/signup/owner/1", student: "/signup/student/1" },
  demo: { owner: "/demo/owner", student: "/demo/student" },
};

const ROLES: { role: Role; description: string }[] = [
  { role: "owner", description: "제안 받기·의뢰하기" },
  { role: "student", description: "제안·지원·공감하기" },
];

/** 피그마 「회원가입 - 역할 선택 (카카오 로그인)」 · 「둘러보기 - 역할 선택 (로그인 없이)」 */
function RoleSelectPage({ mode }: RoleSelectPageProps) {
  const navigate = useNavigate();

  return (
    <div className="role-select">
      <AppBar title={TITLES[mode]} onBack={() => navigate("/login")} />

      <main className="role-select__content">
        <div className="role-select__brand">
          <AppImage name="logoGakkum" priority />
          <AppImage name="taglineRole" priority />
        </div>

        <div className="role-select__cards">
          {ROLES.map(({ role, description }) => (
            <RoleCard
              key={role}
              role={role}
              description={description}
              actionLabel={ACTIONS[mode]}
              onSelect={() => navigate(NEXT_PATHS[mode][role])}
            />
          ))}
        </div>
      </main>
    </div>
  );
}

export default RoleSelectPage;
