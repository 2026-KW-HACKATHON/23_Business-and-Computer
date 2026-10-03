import { Navigate } from "react-router-dom";
import { landingPath } from "../features/auth";

/** 없는 주소. /home 을 거치지 않고 로그인 상태·역할에 맞는 첫 화면으로 바로 보낸다 (ADR 0015) */
function LandingRedirect() {
  return <Navigate to={landingPath()} replace />;
}

export default LandingRedirect;
