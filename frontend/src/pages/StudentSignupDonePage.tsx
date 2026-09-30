import { Navigate, useNavigate } from "react-router-dom";
import { AppImage, Button } from "../components";
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
        {/* 학생 홈(/student)은 아직 없어 만들어지면 연결된다 */}
        <Button fullWidth tone="student" onClick={() => navigate("/student", { replace: true })}>
          시작하기
        </Button>
      </footer>
    </div>
  );
}

export default StudentSignupDonePage;
