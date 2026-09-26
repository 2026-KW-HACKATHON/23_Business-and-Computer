import { useNavigate } from "react-router-dom";
import { AppBar, AppImage } from "../components";
import type { ImageName } from "../components";
import type { Role } from "../types/role";
import "./RoleSelectPage.css";

type RoleSelectMode = "signup" | "demo";

interface RoleSelectPageProps {
  /** signup = 카카오 로그인 후 회원가입, demo = 로그인 없이 둘러보기 */
  mode: RoleSelectMode;
}

const TITLES: Record<RoleSelectMode, string> = {
  signup: "회원가입 - 유형 선택",
  demo: "둘러보기(Demo) - 유형 선택",
};

const ACTIONS: Record<RoleSelectMode, string> = {
  signup: "으로 시작하기",
  demo: "으로 둘러보기",
};

/** 화면 상태 전환표의 라우트 제안. 아직 없는 화면은 만들어지면 연결된다. */
const NEXT_PATHS: Record<RoleSelectMode, Record<Role, string>> = {
  signup: { owner: "/signup/owner/1", student: "/signup/student/1" },
  demo: { owner: "/demo/owner", student: "/demo/student" },
};

const ROLES: { role: Role; name: string; character: ImageName; badge: ImageName }[] = [
  { role: "owner", name: "사장님", character: "characterOwner", badge: "characterBadgeOwner" },
  { role: "student", name: "대학생", character: "characterStudent", badge: "characterBadgeStudent" },
];

/** 피그마 「회원가입 - 역할 선택 (카카오 로그인)」 · 「둘러보기 - 역할 선택 (로그인 없이)」 */
function RoleSelectPage({ mode }: RoleSelectPageProps) {
  const navigate = useNavigate();
  const select = (role: Role) => navigate(NEXT_PATHS[mode][role]);

  return (
    <div className="role-select">
      <AppBar title={TITLES[mode]} onBack={() => navigate("/login")} />

      <main className="role-select__content">
        <div className="role-select__brand">
          <AppImage name="logoGakkum" priority />
          <AppImage name="tagline" className="role-select__tagline" priority />
        </div>

        <div className="role-select__characters">
          {ROLES.map(({ role, name, character }) => (
            <button
              key={role}
              type="button"
              className="role-select__character"
              onClick={() => select(role)}
              aria-label={`${name}${ACTIONS[mode]}`}
            >
              <AppImage name={character} width={136} alt="" priority />
            </button>
          ))}
        </div>

        <div className="role-select__buttons">
          {ROLES.map(({ role, name, badge }) => (
            <button
              key={role}
              type="button"
              className="role-select__button"
              onClick={() => select(role)}
            >
              <AppImage name={badge} className="role-select__badge" priority />
              <span className="role-select__label">
                <strong className="role-select__name">{name}</strong>
                {ACTIONS[mode]}
              </span>
              <AppImage name="iconChevronRight20" />
            </button>
          ))}
        </div>
      </main>
    </div>
  );
}

export default RoleSelectPage;
