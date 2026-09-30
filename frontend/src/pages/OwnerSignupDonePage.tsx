import { Navigate, useNavigate } from "react-router-dom";
import { AppImage, Button } from "../components";
import { useOwnerSignup } from "../features/signup";
import "./OwnerSignupPage.css";
import "./OwnerSignupDonePage.css";

/** 피그마 「회원가입 완료(사장님)」 */
function OwnerSignupDonePage() {
  const navigate = useNavigate();
  const { draft } = useOwnerSignup();

  if (!draft.completed) return <Navigate to="/signup/role" replace />;

  return (
    <div className="owner-signup owner-signup-done">
      <main className="owner-signup-done__body">
        <AppImage name="doneOwner" priority />
        <h1 className="owner-signup-done__title">{`${draft.name.trim()} 사장님,\n가입을 환영해요`}</h1>
        <p className="owner-signup-done__description">{"학생 제안이 오면\n문자로 알려드릴게요"}</p>
      </main>

      <footer className="owner-signup__footer">
        {/* 사장님 홈(/owner)은 아직 없어 만들어지면 연결된다 */}
        <Button fullWidth onClick={() => navigate("/owner", { replace: true })}>
          시작하기
        </Button>
      </footer>
    </div>
  );
}

export default OwnerSignupDonePage;
