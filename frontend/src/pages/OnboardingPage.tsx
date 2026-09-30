import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppImage } from "../components";
import { markOnboardingSeen } from "../features/onboarding";
import "./OnboardingPage.css";

/** 한 단계씩 차례로 나타난다 (0.4초 간격, 5단계면 약 2초) */
const FLOW_STEPS = ["제안 또는 의뢰", "작업 시작", "초안", "수정", "완료"];

/** 다 나타난 뒤 1초 더 머물러 모두 3초 */
const AUTO_LEAVE_MS = 3000;
const FADE_OUT_MS = 300;

/** 피그마 「1. 공통 (온보딩·로그인)」 › 로그인 전 히어로 (잠깐 보임) */
function OnboardingPage() {
  const navigate = useNavigate();
  const [leaving, setLeaving] = useState(false);
  const left = useRef(false);

  // 본 것으로 저장하고 로그인으로 간다. 뒤로 가기로 돌아오지 않게 교체한다.
  const leave = useCallback(
    (fade: boolean) => {
      if (left.current) return;
      left.current = true;
      markOnboardingSeen();
      if (!fade) {
        navigate("/login", { replace: true });
        return;
      }
      setLeaving(true);
      window.setTimeout(() => navigate("/login", { replace: true }), FADE_OUT_MS);
    },
    [navigate],
  );

  useEffect(() => {
    const timer = window.setTimeout(() => leave(true), AUTO_LEAVE_MS);
    return () => window.clearTimeout(timer);
  }, [leave]);

  return (
    <button
      type="button"
      className={`onboarding${leaving ? " onboarding--leaving" : ""}`}
      onClick={() => leave(false)}
      aria-label="가꿈 소개. 누르면 로그인으로 넘어가요"
    >
      <span className="onboarding__hero">
        <AppImage name="appIcon" className="onboarding__app-icon" priority />
        <span className="onboarding__title">{"월계1동 가게와 광운대생,\n가꿈에서 만나요"}</span>
      </span>

      <span className="onboarding__flow">
        <span className="onboarding__flow-title">저희 서비스의 흐름이에요</span>
        <span className="onboarding__steps">
          {FLOW_STEPS.map((label, i) => (
            <span
              key={label}
              className="onboarding__step"
              style={{ animationDelay: `${i * 0.4}s` }}
            >
              <span className="onboarding__step-number">{i + 1}</span>
              {label}
            </span>
          ))}
        </span>
      </span>

      <span className="onboarding__note">작업비는 가꿈이 맡아 두었다가 완료되면 학생에게 보내요</span>
    </button>
  );
}

export default OnboardingPage;
