import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Loader } from "../components";
import {
  clearDemoSession,
  exchangeCookieForAccessToken,
  landingPath,
  saveAccessToken,
} from "../features/auth";

/**
 * Landing route after a successful social login. The backend has set an
 * HTTP-only refresh-token cookie and redirected here; we exchange that cookie
 * for an access token, store it, then go to the screen for the user's role
 * (signup not finished → role select, owner → owner home).
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
        const accessToken = await exchangeCookieForAccessToken();
        // 둘러보던 중에 카카오로 로그인하면 데모 표시를 지운다
        clearDemoSession();
        saveAccessToken(accessToken);
        navigate(landingPath(), { replace: true });
      } catch {
        navigate("/login", { replace: true });
      }
    };

    void run();
  }, [navigate]);

  return <Loader label="로그인하는 중이에요" overlay />;
}

export default CookiePage;
