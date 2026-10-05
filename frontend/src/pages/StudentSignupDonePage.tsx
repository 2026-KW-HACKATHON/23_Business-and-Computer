import { Navigate, useNavigate } from "react-router-dom";
import { AppImage, Button } from "../components";
import { landingPath } from "../features/auth";
import { useStudentSignup } from "../features/signup";
import "./SignupPage.css";
import "./SignupDonePage.css";

/** 피그마 「회원가입 완료(학생)」 */
function StudentSignupDonePage() {
  const navigate = useNavigate();
  const { draft } = useStudentSignup();

  if (!draft.completed) return <Navigate to="/signup/role" replace />;

  return (
    <div className="signup signup-done">
      <main className="signup-done__body">
        <AppImage name="doneStudent" priority />
        <h1 className="signup-done__title">{`${draft.name.trim()} 학생,\n가입을 환영해요`}</h1>
        <p className="signup-done__description">{"동네 가게를 바꿀 첫 제안을\n지금 시작해 보세요"}</p>
      </main>

      <footer className="signup__footer">
        {/* 가입 때 받은 STUDENT 토큰 기준으로 첫 화면을 고른다 (ADR 0015) */}
        <Button fullWidth tone="student" onClick={() => navigate(landingPath(), { replace: true })}>
          시작하기
        </Button>
      </footer>
    </div>
  );
}

export default StudentSignupDonePage;
