import type { SocialProvider } from "../features/auth";
import { socialLoginUrl } from "../features/auth";
import "./LoginPage.css";

const PROVIDERS: { id: SocialProvider; label: string }[] = [
  { id: "kakao", label: "Kakao로 계속하기" },
  { id: "google", label: "Google로 계속하기" },
  { id: "naver", label: "Naver로 계속하기" },
];

function LoginPage() {
  const handleSocialLogin = (provider: SocialProvider) => {
    // Full-page redirect to the backend OAuth2 entry point.
    window.location.assign(socialLoginUrl(provider));
  };

  return (
    <main className="login">
      <h1 className="login__title">로그인</h1>
      <p className="login__subtitle">소셜 계정으로 가꿈에 로그인하세요.</p>

      <div className="login__providers">
        {PROVIDERS.map(({ id, label }) => (
          <button
            key={id}
            type="button"
            className={`login__button login__button--${id}`}
            onClick={() => handleSocialLogin(id)}
          >
            {label}
          </button>
        ))}
      </div>
    </main>
  );
}

export default LoginPage;
