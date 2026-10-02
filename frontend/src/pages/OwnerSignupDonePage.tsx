import { Navigate, useNavigate } from "react-router-dom";
import { AppImage, Button } from "../components";
import { useOwnerSignup } from "../features/signup";
import "./SignupPage.css";
import "./SignupDonePage.css";

/** 피그마 「회원가입 완료(사장님)」 */
function OwnerSignupDonePage() {
  const navigate = useNavigate();
  const { draft } = useOwnerSignup();

  if (!draft.completed) return <Navigate to="/signup/role" replace />;

  return (
    <div className="signup signup-done">
      <main className="signup-done__body">
        <AppImage name="doneOwner" priority />
        <h1 className="signup-done__title">{`${draft.name.trim()} 사장님,\n가입을 환영해요`}</h1>
        <p className="signup-done__description">{"학생 제안이 오면\n인앱 알림으로 알려드릴게요"}</p>
      </main>

      <footer className="signup__footer">
        <Button fullWidth onClick={() => navigate("/owner", { replace: true })}>
          시작하기
        </Button>
      </footer>
    </div>
  );
}

export default OwnerSignupDonePage;
