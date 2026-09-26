import { useNavigate } from "react-router-dom";
import { AppImage, DemoButton, KakaoLoginButton, TextButton } from "../components";
import { socialLoginUrl } from "../features/auth";
import "./LoginPage.css";

const NOTION_URL = "https://app.notion.com/p/2026-KW-0a0934d4ce52829080af81f185aacf3b";

/** 피그마 「1. 공통 (온보딩·로그인)」 › 로그인 */
function LoginPage() {
  const navigate = useNavigate();

  const handleKakaoLogin = () => {
    // 백엔드 OAuth2 입구로 전체 페이지 이동. 성공하면 백엔드가 /cookie 로 돌려보낸다.
    window.location.assign(socialLoginUrl("kakao"));
  };

  return (
    <div className="login">
      <main className="login__body">
        <div className="login__hero">
          <AppImage name="appIcon" className="login__app-icon" priority />
          <div className="login__copy">
            <h1 className="login__title">{"월계1동 가게와 광운대생,\n가꿈에서 만나요"}</h1>
            <p className="login__description">사장님은 의뢰하고, 학생은 제안해요</p>
          </div>
        </div>
      </main>

      <footer className="login__footer">
        <div className="login__buttons">
          <KakaoLoginButton onClick={handleKakaoLogin} />
          <DemoButton onClick={() => navigate("/demo/role")} />
        </div>
        <div className="login__notion">
          <AppImage name="logoNotion" />
          <TextButton onClick={() => window.open(NOTION_URL, "_blank", "noopener,noreferrer")}>
            노션에서 개발 과정 보기
          </TextButton>
        </div>
      </footer>
    </div>
  );
}

export default LoginPage;
