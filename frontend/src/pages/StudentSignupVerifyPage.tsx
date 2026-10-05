import { useEffect, useRef, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { AppBar, Button, ResendButton, StepIndicator, TextField } from "../components";
import { landingPath } from "../features/auth";
import {
  MAX_CODE_ATTEMPTS,
  RESEND_COOLDOWN_MS,
  VERIFICATION_CODE_TTL_MS,
  formatRemaining,
  isSchoolEmail,
  isStudentInfoComplete,
  sendVerificationCode,
  useStudentSignup,
  verifyCode,
} from "../features/signup";
import type { StudentVerifyReturnState } from "../features/signup";
import "./SignupPage.css";
import "./StudentSignupVerifyPage.css";

type EmailError = "domain" | "taken" | null;
type CodeError = "mismatch" | "expired" | "tooMany" | null;
/** 입력칸 테두리와 상관없는 안내. reverify 는 가입 저장(3/3)에서 돌려보낸 경우 */
type Notice = "reverify" | "deliveryFailed" | "error" | null;
type Pending = "send" | "verify" | null;

const EMAIL_ERROR_TEXT: Record<Exclude<EmailError, null>, string> = {
  domain: "@kw.ac.kr 메일만 가입할 수 있어요",
  taken: "이미 다른 계정에서 사용 중인 메일이에요",
};

const CODE_ERROR_TEXT: Record<Exclude<CodeError, null>, string> = {
  mismatch: "인증번호가 일치하지 않아요",
  expired: "인증 시간이 지났어요. 인증번호를 다시 받아 주세요",
  tooMany: "여러 번 틀렸어요. 인증번호를 다시 받아 주세요",
};

const NOTICE_TEXT: Record<Exclude<Notice, null>, string> = {
  reverify: "메일 인증을 다시 해 주세요",
  deliveryFailed: "메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요",
  error: "잠시 후 다시 시도해 주세요",
};

/**
 * 피그마 「학생 인증 (광운대 이메일) 2/3」.
 * 상태 = 발송 전 / 발송 후 / 오류 · 메일 도메인 / 오류 · 인증번호 (화면 하나 + 상태값)
 * 요청 중·재발송 제한·서버 오류는 피그마에 없어 버튼 문구와 안내 문구로만 보여준다 (ADR 0019).
 */
function StudentSignupVerifyPage() {
  const navigate = useNavigate();
  const returned = useLocation().state as StudentVerifyReturnState | null;
  const { draft, update } = useStudentSignup();
  const [email, setEmail] = useState(draft.verifiedEmail);
  const [sent, setSent] = useState(false);
  const [emailError, setEmailError] = useState<EmailError>(
    returned?.notice === "emailTaken" ? "taken" : null,
  );
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<CodeError>(null);
  const [failedAttempts, setFailedAttempts] = useState(0);
  const [notice, setNotice] = useState<Notice>(returned?.notice === "reverify" ? "reverify" : null);
  const [resent, setResent] = useState(false);
  const [expiresAt, setExpiresAt] = useState(0);
  const [cooldownUntil, setCooldownUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  // 요청 중 여부는 이 화면에만 둔다. 요청 중에는 입력칸을 잠가서 응답과 화면이 어긋나지 않게 한다
  const [pending, setPending] = useState<Pending>(null);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestId = useRef(0);
  // 마지막으로 발송에 성공한 시각. 재발송 제한(429)이 언제 풀리는지 계산한다
  const lastSentAt = useRef(0);

  useEffect(() => {
    const latest = requestId;
    return () => {
      latest.current += 1;
    };
  }, []);

  // 발송 후나 재발송 제한 중에는 1초마다 남은 시간을 다시 그린다
  const ticking = sent || cooldownUntil > now;
  useEffect(() => {
    if (!ticking) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [ticking]);

  // 1단계를 건너뛰고 들어오면(새로고침 포함) 역할 선택부터 다시
  if (!isStudentInfoComplete(draft)) return <Navigate to="/signup/role" replace />;

  const remaining = expiresAt - now;
  const cooldownLeft = cooldownUntil - now;
  const coolingDown = cooldownLeft > 0;
  const message = coolingDown
    ? `1분 뒤에 다시 보낼 수 있어요 (${Math.ceil(cooldownLeft / 1000)}초)`
    : notice
      ? NOTICE_TEXT[notice]
      : null;

  const leave = (result: "unauthorized" | "alreadyRegistered") => {
    if (result === "unauthorized") {
      navigate("/login", { replace: true });
      return;
    }
    window.alert("이미 가입을 마친 계정이에요");
    navigate(landingPath(), { replace: true });
  };

  // 처음 발송과 재발송이 같은 API 를 쓴다
  const send = async (resend: boolean) => {
    if (!isSchoolEmail(email)) {
      setEmailError("domain");
      return;
    }
    const id = ++requestId.current;
    const target = email.trim();
    setPending("send");
    setNotice(null);

    const result = await sendVerificationCode(target);
    if (id !== requestId.current) return;
    setPending(null);

    switch (result) {
      case "sent": {
        const at = Date.now();
        lastSentAt.current = at;
        // 서버도 새 번호를 보내면 이전 인증 완료를 지우므로 화면도 처음부터 센다
        update({ verifiedEmail: "" });
        setSent(true);
        setCode("");
        setCodeError(null);
        setFailedAttempts(0);
        setResent(resend);
        setCooldownUntil(0);
        setNow(at);
        setExpiresAt(at + VERIFICATION_CODE_TTL_MS);
        break;
      }
      case "cooldown": {
        // 제한은 메일 주소가 아니라 사용자 기준이라 「변경」 후 다른 메일도 막힌다.
        // 서버가 남은 시간을 주지 않아, 이 화면에서 보낸 적이 없으면 지금부터 60초로 본다
        const at = Date.now();
        const sentUntil = lastSentAt.current + RESEND_COOLDOWN_MS;
        setNow(at);
        setCooldownUntil(sentUntil > at ? sentUntil : at + RESEND_COOLDOWN_MS);
        break;
      }
      case "invalidEmail":
      case "emailTaken":
        setSent(false);
        setEmailError(result === "emailTaken" ? "taken" : "domain");
        break;
      case "deliveryFailed":
        setNotice("deliveryFailed");
        break;
      case "unauthorized":
      case "alreadyRegistered":
        leave(result);
        break;
      default:
        setNotice("error");
    }
  };

  const handleChangeEmail = () => {
    setSent(false);
    setCode("");
    setCodeError(null);
    setNotice(null);
  };

  const handleVerify = async () => {
    if (remaining <= 0) {
      setCodeError("expired");
      return;
    }
    if (failedAttempts >= MAX_CODE_ATTEMPTS) {
      setCodeError("tooMany");
      return;
    }
    const id = ++requestId.current;
    const target = email.trim();
    setPending("verify");
    setNotice(null);

    const result = await verifyCode(target, code);
    if (id !== requestId.current) return;
    setPending(null);

    switch (result) {
      case "verified":
        update({ verifiedEmail: target });
        navigate("/signup/student/3");
        break;
      case "invalid": {
        // 서버는 틀림과 만료를 같은 코드로 주므로 화면의 남은 시간으로 나눈다
        if (Date.now() >= expiresAt) {
          setCodeError("expired");
          break;
        }
        const failed = failedAttempts + 1;
        setFailedAttempts(failed);
        setCodeError(failed >= MAX_CODE_ATTEMPTS ? "tooMany" : "mismatch");
        break;
      }
      case "unauthorized":
      case "alreadyRegistered":
        leave(result);
        break;
      default:
        setNotice("error");
    }
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
            readOnly={sent || pending !== null}
            invalid={emailError !== null}
            errorText={emailError ? EMAIL_ERROR_TEXT[emailError] : undefined}
            onChange={(e) => {
              setEmail(e.target.value);
              setEmailError(null);
              setNotice(null);
            }}
            trailing={
              sent ? (
                <button
                  type="button"
                  className="student-signup-verify__change"
                  disabled={pending !== null}
                  onClick={handleChangeEmail}
                >
                  변경
                </button>
              ) : undefined
            }
          />

          {!sent && message && (
            <p className="student-signup-verify__error" role="alert">
              {message}
            </p>
          )}

          {sent && (
            <div>
              <TextField
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                placeholder="인증번호 6자리"
                aria-label="인증번호"
                value={code}
                readOnly={pending !== null}
                invalid={codeError !== null}
                onChange={(e) => {
                  setCode(e.target.value.replace(/\D/g, ""));
                  setCodeError(null);
                  setNotice(null);
                }}
                trailing={<span className="student-signup-verify__timer">{formatRemaining(remaining)}</span>}
              />
              <div className="student-signup-verify__code-row">
                <p className="student-signup-verify__error" role="alert">
                  {codeError ? CODE_ERROR_TEXT[codeError] : message}
                </p>
                <ResendButton
                  sent={resent}
                  disabled={pending !== null || coolingDown}
                  onClick={() => void send(true)}
                />
              </div>
            </div>
          )}
        </div>
      </main>

      <footer className="signup__footer">
        {sent ? (
          <Button
            fullWidth
            tone="student"
            disabled={code.length !== 6 || pending !== null}
            onClick={() => void handleVerify()}
          >
            {pending === "verify" ? "확인 중..." : "인증 완료"}
          </Button>
        ) : (
          <Button
            fullWidth
            tone="student"
            disabled={email.trim() === "" || emailError !== null || pending !== null || coolingDown}
            onClick={() => void send(false)}
          >
            {pending === "send" ? "보내는 중..." : "인증번호 발송"}
          </Button>
        )}
      </footer>
    </div>
  );
}

export default StudentSignupVerifyPage;
