import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AppBar, Button, ResendButton, StepIndicator, TextField } from "../components";
import {
  MOCK_VERIFICATION_CODE,
  VERIFICATION_CODE_TTL_MS,
  formatRemaining,
  isSchoolEmail,
  isStudentInfoComplete,
  useStudentSignup,
} from "../features/signup";
import "./SignupPage.css";
import "./StudentSignupVerifyPage.css";

type CodeError = "mismatch" | "expired" | null;

/**
 * 피그마 「학생 인증 (광운대 이메일) 2/3」.
 * 상태 = 발송 전 / 발송 후 / 오류 · 메일 도메인 / 오류 · 인증번호 (화면 하나 + 상태값)
 */
function StudentSignupVerifyPage() {
  const navigate = useNavigate();
  const { draft, update } = useStudentSignup();
  const [email, setEmail] = useState(draft.verifiedEmail);
  const [sent, setSent] = useState(false);
  const [domainError, setDomainError] = useState(false);
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<CodeError>(null);
  const [resent, setResent] = useState(false);
  const [expiresAt, setExpiresAt] = useState(0);
  const [now, setNow] = useState(() => Date.now());

  // 발송 후에는 1초마다 남은 시간을 다시 그린다
  useEffect(() => {
    if (!sent) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [sent]);

  // 1단계를 건너뛰고 들어오면(새로고침 포함) 역할 선택부터 다시
  if (!isStudentInfoComplete(draft)) return <Navigate to="/signup/role" replace />;

  const remaining = expiresAt - now;

  const startTimer = () => {
    const at = Date.now();
    setNow(at);
    setExpiresAt(at + VERIFICATION_CODE_TTL_MS);
  };

  // 백엔드 연동 전: 메일은 보내지 않고 발송 후 화면으로만 넘어간다
  const handleSend = () => {
    if (!isSchoolEmail(email)) {
      setDomainError(true);
      return;
    }
    setSent(true);
    setCode("");
    setCodeError(null);
    setResent(false);
    startTimer();
  };

  const handleChangeEmail = () => {
    setSent(false);
    setCode("");
    setCodeError(null);
  };

  const handleResend = () => {
    setResent(true);
    setCodeError(null);
    startTimer();
  };

  const handleVerify = () => {
    if (remaining <= 0) {
      setCodeError("expired");
      return;
    }
    if (code !== MOCK_VERIFICATION_CODE) {
      setCodeError("mismatch");
      return;
    }
    update({ verifiedEmail: email.trim() });
    navigate("/signup/student/3");
  };

  return (
    <div className="signup">
      <AppBar
        title="학생 인증(메일 인증)"
        onBack={() => navigate("/signup/student/1")}
        muted
        bottom={<StepIndicator total={3} current={2} tone="student" />}
      />

      <main className="signup__body">
        <p className="student-signup-verify__guide" aria-live="polite">
          {sent ? "메일로 인증번호를 보냈어요. 메일함을 확인해 주세요" : "학생 인증을 위해 학교 이메일을 사용해주세요"}
        </p>

        <div className="signup__fields">
          <TextField
            type="email"
            inputMode="email"
            autoComplete="email"
            placeholder="@kw.ac.kr 형식으로 입력해주세요."
            aria-label="학교 이메일"
            value={email}
            readOnly={sent}
            invalid={domainError}
            errorText={domainError ? "@kw.ac.kr 메일만 가입할 수 있어요" : undefined}
            onChange={(e) => {
              setEmail(e.target.value);
              setDomainError(false);
            }}
            trailing={
              sent ? (
                <button type="button" className="student-signup-verify__change" onClick={handleChangeEmail}>
                  변경
                </button>
              ) : undefined
            }
          />

          {sent && (
            <div>
              <TextField
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                placeholder="인증번호 6자리"
                aria-label="인증번호"
                value={code}
                invalid={codeError !== null}
                onChange={(e) => {
                  setCode(e.target.value.replace(/\D/g, ""));
                  setCodeError(null);
                }}
                trailing={<span className="student-signup-verify__timer">{formatRemaining(remaining)}</span>}
              />
              <div className="student-signup-verify__code-row">
                <p className="student-signup-verify__error" role="alert">
                  {codeError === "mismatch" && "인증번호가 일치하지 않아요"}
                  {codeError === "expired" && "시간이 지났어요"}
                </p>
                <ResendButton sent={resent} onClick={handleResend} />
              </div>
            </div>
          )}
        </div>
      </main>

      <footer className="signup__footer">
        {sent ? (
          <Button fullWidth tone="student" disabled={code.length !== 6} onClick={handleVerify}>
            인증 완료
          </Button>
        ) : (
          <Button
            fullWidth
            tone="student"
            disabled={email.trim() === "" || domainError}
            onClick={handleSend}
          >
            인증번호 발송
          </Button>
        )}
      </footer>
    </div>
  );
}

export default StudentSignupVerifyPage;
