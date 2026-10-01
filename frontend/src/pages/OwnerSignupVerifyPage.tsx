import { useEffect, useRef } from "react";
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
 * 상태 = 인증 전 / 인증 완료 / 오류 · 정보 불일치 (화면 하나 + 상태값 하나)
 * 인증 중·서버 오류는 피그마에 없어 버튼 문구와 안내 문구로만 보여준다 (ADR 0012).
 */
function OwnerSignupVerifyPage() {
  const navigate = useNavigate();
  const { draft, update } = useOwnerSignup();
  const { business } = draft;
  const { check } = business;
  const verified = check === "verified";
  const checking = check === "checking";
  // 응답 전에 값을 고치거나 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestId = useRef(0);

  useEffect(() => {
    const latest = requestId;
    return () => {
      latest.current += 1;
    };
  }, []);

  // 1단계를 건너뛰고 들어오면(새로고침 포함) 역할 선택부터 다시
  if (!isStoreInfoComplete(draft)) return <Navigate to="/signup/role" replace />;

  // 값을 고치면 오류 표시를 지우고 인증 전으로 돌아간다
  const edit = (patch: Partial<BusinessInfo>) => {
    requestId.current += 1;
    update({ business: { ...business, ...patch, check: "idle" } });
  };

  const filled =
    business.number.trim() !== "" &&
    business.openedAt.trim() !== "" &&
    business.representative.trim() !== "";

  const handleVerify = async () => {
    const id = ++requestId.current;
    const info = business;
    update({ business: { ...info, check: "checking" } });

    const result = await checkBusinessInfo(info);
    if (id !== requestId.current) return;

    if (result === "unauthorized") {
      navigate("/login", { replace: true });
    } else if (result === "alreadyRegistered") {
      window.alert("이미 가입을 마친 계정이에요");
      navigate("/home", { replace: true });
    } else {
      update({ business: { ...info, check: result } });
    }
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
            invalid={check === "mismatch"}
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

        {check === "error" && (
          <p className="owner-signup-verify__error" role="alert">
            잠시 후 다시 시도해 주세요
          </p>
        )}

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
          <Button fullWidth disabled={!filled || checking} onClick={() => void handleVerify()}>
            {checking ? "인증 중..." : "인증하기"}
          </Button>
        )}
      </footer>
    </div>
  );
}

export default OwnerSignupVerifyPage;
