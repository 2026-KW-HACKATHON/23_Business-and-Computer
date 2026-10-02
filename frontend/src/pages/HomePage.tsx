import { useNavigate } from "react-router-dom";
import { clearTokens, isLoggedIn } from "../features/auth";

function HomePage() {
  const navigate = useNavigate();
  const loggedIn = isLoggedIn();

  const handleLogout = () => {
    clearTokens();
    navigate("/login", { replace: true });
  };

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
