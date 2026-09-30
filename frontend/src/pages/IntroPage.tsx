import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppImage } from "../components";
import { isLoggedIn } from "../features/auth";
import "./IntroPage.css";

/** 한 단계씩 차례로 나타난다 (0.4초 간격, 5단계면 약 2초) */
const FLOW_STEPS = ["제안 또는 의뢰", "작업 시작", "초안", "수정", "완료"];

/** 다 나타난 뒤 1초 더 머물러 모두 3초 */
const AUTO_LEAVE_MS = 3000;
const FADE_OUT_MS = 300;

/**
 * 피그마 「1. 공통 (온보딩·로그인)」 › 로그인 전 히어로 (잠깐 보임).
 * 스플래시 대신 앱을 열 때마다 처음 보이는 화면이다.
 */
function IntroPage() {
  const navigate = useNavigate();
  const [leaving, setLeaving] = useState(false);
  const left = useRef(false);

  // 로그인했으면 홈, 아니면 로그인으로 간다. 뒤로 가기로 돌아오지 않게 교체한다.
  const leave = useCallback(
    (fade: boolean) => {
      if (left.current) return;
      left.current = true;
      const next = isLoggedIn() ? "/home" : "/login";
      if (!fade) {
        navigate(next, { replace: true });
        return;
      }
      setLeaving(true);
      window.setTimeout(() => navigate(next, { replace: true }), FADE_OUT_MS);
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
      className={`intro${leaving ? " intro--leaving" : ""}`}
      onClick={() => leave(false)}
      aria-label="가꿈 소개. 누르면 다음 화면으로 넘어가요"
    >
      <span className="intro__hero">
        <AppImage name="appIcon" className="intro__app-icon" priority />
        <span className="intro__title">{"월계1동 가게와 광운대생,\n가꿈에서 만나요"}</span>
      </span>

      <span className="intro__flow">
        <span className="intro__flow-title">저희 서비스의 흐름이에요</span>
        <span className="intro__steps">
          {FLOW_STEPS.map((label, i) => (
            <span key={label} className="intro__step" style={{ animationDelay: `${i * 0.4}s` }}>
              <span className="intro__step-number">{i + 1}</span>
              {label}
            </span>
          ))}
        </span>
      </span>

      <span className="intro__note">작업비는 가꿈이 맡아 두었다가 완료되면 학생에게 보내요</span>
    </button>
  );
}

export default IntroPage;
