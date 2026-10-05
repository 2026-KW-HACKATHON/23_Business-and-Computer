import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppBar, AppImage, RoleCard } from "../components";
import { landingPath, startDemo } from "../features/auth";
import type { DemoLoginResult } from "../features/auth";
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

const SIGNUP_PATHS: Record<Role, string> = {
  owner: "/signup/owner/1",
  student: "/signup/student/1",
};

const DEMO_ERRORS: Partial<Record<DemoLoginResult, string>> = {
  limit: "지금은 둘러보기를 더 열 수 없어요. 잠시 후 다시 시도해 주세요",
  failed: "둘러보기를 시작하지 못했어요. 잠시 후 다시 시도해 주세요",
};

const ROLES: { role: Role; description: string }[] = [
  { role: "owner", description: "제안 받기·의뢰하기" },
  { role: "student", description: "제안·지원·공감하기" },
];

/**
 * 피그마 「회원가입 - 역할 선택 (카카오 로그인)」 · 「둘러보기 - 역할 선택 (로그인 없이)」.
 * 둘러보기는 POST /demo/login 으로 데모 계정에 들어가 그 역할의 홈으로 간다.
 */
function RoleSelectPage({ mode }: RoleSelectPageProps) {
  const navigate = useNavigate();
  const [entering, setEntering] = useState<Role | null>(null);
  const [error, setError] = useState("");
  // 다시 그려지기 전에 두 번 눌러도 데모 계정은 한 번만 만든다
  const inFlight = useRef(false);

  const select = (role: Role) => {
    if (mode === "signup") {
      navigate(SIGNUP_PATHS[role]);
      return;
    }
    if (inFlight.current) return;
    inFlight.current = true;
    setEntering(role);
    setError("");
    void startDemo(role).then((result) => {
      inFlight.current = false;
      setEntering(null);
      if (result === "ok") navigate(landingPath(), { replace: true });
      else setError(DEMO_ERRORS[result] ?? DEMO_ERRORS.failed ?? "");
    });
  };

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
              actionLabel={entering === role ? "들어가는 중..." : ACTIONS[mode]}
              disabled={entering !== null}
              onSelect={() => select(role)}
            />
          ))}
        </div>

        {error && <p className="role-select__error">{error}</p>}
      </main>
    </div>
  );
}

export default RoleSelectPage;
