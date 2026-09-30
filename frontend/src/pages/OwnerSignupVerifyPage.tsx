import { Navigate, useNavigate } from "react-router-dom";
import { AppBar, Button, StepIndicator, TextField } from "../components";
import {
  checkBusinessInfo,
  formatBusinessNumber,
  isStoreInfoComplete,
  useOwnerSignup,
} from "../features/signup";
import type { BusinessInfo } from "../features/signup";
import "./SignupPage.css";
import "./OwnerSignupVerifyPage.css";

/**
 * 피그마 「회원가입 - 프로필 입력(사장님) 2/3 · 사장님 인증」.
 * 상태 = 인증 전 / 인증 완료 / 오류 · 정보 불일치 / 오류 · 휴업·폐업 (화면 하나 + 상태값 하나)
 */
function OwnerSignupVerifyPage() {
  const navigate = useNavigate();
  const { draft, update } = useOwnerSignup();
  const { business } = draft;
  const { check } = business;
  const verified = check === "verified";

  // 1단계를 건너뛰고 들어오면(새로고침 포함) 역할 선택부터 다시
  if (!isStoreInfoComplete(draft)) return <Navigate to="/signup/role" replace />;

  // 값을 고치면 오류 표시를 지우고 인증 전으로 돌아간다
  const edit = (patch: Partial<BusinessInfo>) => {
    update({ business: { ...business, ...patch, check: "idle" } });
  };

  const filled =
    business.number.trim() !== "" &&
    business.openedAt.trim() !== "" &&
    business.representative.trim() !== "";

  const handleVerify = () => {
    update({ business: { ...business, check: checkBusinessInfo(business) } });
  };

  return (
    <div className="signup">
      <AppBar
        title="사장님 인증"
        onBack={() => navigate("/signup/owner/1")}
        muted
        bottom={<StepIndicator total={3} current={2} tone="owner" />}
      />

      <main className="signup__body">
        <h2 className="signup__section-title">가게 인증하기</h2>
        <div className="signup__fields">
          <TextField
            placeholder="사업자등록번호"
            aria-label="사업자등록번호"
            inputMode="numeric"
            value={business.number}
            readOnly={verified}
            invalid={check === "mismatch" || check === "closed"}
            errorText={check === "closed" ? "휴업·폐업한 사업자는 가입할 수 없어요" : undefined}
            onChange={(e) => edit({ number: formatBusinessNumber(e.target.value) })}
          />
          <TextField
            placeholder="개업일 (YYYYMMDD-숫자 8자리 입력)"
            aria-label="개업일 (YYYYMMDD)"
            inputMode="numeric"
            maxLength={8}
            value={business.openedAt}
            readOnly={verified}
            invalid={check === "mismatch"}
            onChange={(e) => edit({ openedAt: e.target.value.replace(/\D/g, "") })}
          />
          <TextField
            placeholder="대표자 이름"
            aria-label="대표자 이름"
            value={business.representative}
            readOnly={verified}
            invalid={check === "mismatch"}
            errorText={check === "mismatch" ? "사업자 정보가 일치하지 않아요" : undefined}
            onChange={(e) => edit({ representative: e.target.value })}
          />
        </div>

        {verified && (
          <div className="owner-signup-verify__done" role="status">
            <span className="owner-signup-verify__success">가게 인증이 완료되었어요</span>
            <button type="button" className="owner-signup-verify__edit" onClick={() => edit({})}>
              정보 수정
            </button>
          </div>
        )}
      </main>

      <footer className="signup__footer">
        {verified ? (
          <Button fullWidth onClick={() => navigate("/signup/owner/3")}>
            다음
          </Button>
        ) : (
          <Button fullWidth disabled={!filled} onClick={handleVerify}>
            인증하기
          </Button>
        )}
      </footer>
    </div>
  );
}

export default OwnerSignupVerifyPage;
