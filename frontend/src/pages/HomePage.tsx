import { Navigate, useNavigate } from "react-router-dom";
import { clearTokens, isLoggedIn, landingPath } from "../features/auth";

/**
 * 임시 홈. 없는 주소도 여기로 온다. 가입 전이거나 사장님이면 맞는 화면으로 보내고,
 * 학생 홈을 만들기 전까지 학생은 여기 머문다.
 */
function HomePage() {
  const navigate = useNavigate();
  const loggedIn = isLoggedIn();
  const next = landingPath();

  const handleLogout = () => {
    clearTokens();
    navigate("/login", { replace: true });
  };

  if (next !== "/home" && next !== "/login") return <Navigate to={next} replace />;

  return (
    <main style={{ textAlign: "center", marginTop: "4rem" }}>
      <h1>가꿈</h1>
      {loggedIn ? (
        <>
          <p>로그인되었습니다.</p>
          <button type="button" onClick={handleLogout}>
            로그아웃
          </button>
        </>
      ) : (
        <button type="button" onClick={() => navigate("/login")}>
          로그인하러 가기
        </button>
      )}
    </main>
  );
}

export default HomePage;
