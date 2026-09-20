import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { exchangeCookieForTokens, saveTokens } from "../features/auth";

/**
 * Landing route after a successful social login. The backend has set an
 * HTTP-only JWT cookie and redirected here; we exchange that cookie for a JWT
 * pair in the response body, store it, then move on.
 */
function CookiePage() {
  const navigate = useNavigate();
  // The exchange consumes a one-time refresh token, so it must run exactly once.
  // This guard blocks React StrictMode's dev double-invoke of the effect, which
  // would otherwise fire two concurrent requests that race to delete the same
  // refresh-token row.
  const exchanged = useRef(false);

  useEffect(() => {
    if (exchanged.current) return;
    exchanged.current = true;

    const run = async () => {
      try {
        const tokens = await exchangeCookieForTokens();
        saveTokens(tokens);
        navigate("/", { replace: true });
      } catch {
        navigate("/login", { replace: true });
      }
    };

    void run();
  }, [navigate]);

  return <p style={{ textAlign: "center", marginTop: "4rem" }}>로그인 처리 중입니다...</p>;
}

export default CookiePage;
