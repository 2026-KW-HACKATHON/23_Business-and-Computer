import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppBar, Button, Checkbox, StepIndicator, TextField, UniversityField } from "../components";
import { TermsSheet, isStudentInfoComplete, isStudentNumber, useStudentSignup } from "../features/signup";
import { usePushed } from "../hooks/usePushed";
import "./SignupPage.css";
import "./StudentSignupInfoPage.css";

/** 피그마 「회원가입 - 정보입력(학생) 1/3」 */
function StudentSignupInfoPage() {
  const pushed = usePushed();
  const navigate = useNavigate();
  const { draft, update } = useStudentSignup();
  const [termsOpen, setTermsOpen] = useState(false);
  // 학번 형식 오류는 칸을 벗어난 뒤에만 보여준다. 고쳐서 맞으면 다시 벗어날 때까지 숨긴다
  const [studentNumberTouched, setStudentNumberTouched] = useState(false);
  // 가입 저장(3/3)에서 이미 가입된 학번이라고 돌아온 번호면 바로 보여주고 고칠 때까지 막는다
  const taken = draft.studentNumber !== "" && draft.studentNumber === draft.takenStudentNumber;
  const studentNumberError = taken
    ? "이미 가입된 학번이에요"
    : studentNumberTouched && !isStudentNumber(draft.studentNumber)
      ? "학번 10자리를 입력해 주세요"
      : undefined;
  const complete = isStudentInfoComplete(draft);

  // 「다음」은 비활성이면 눌리지 않지만, 눌렸는데 틀렸다면 오류를 보여준다
  const handleNext = () => {
    if (!complete) {
      setStudentNumberTouched(true);
      return;
    }
    navigate("/signup/student/2");
  };

  return (
    <div className={`signup${pushed ? " screen-pushed" : ""}`}>
      <AppBar
        title="정보 입력"
        onBack={() => navigate("/signup/role")}
        muted
        bottom={<StepIndicator total={3} current={1} tone="student" />}
      />

      <main className="signup__body">
        <div className="signup__fields">
          <UniversityField />
          <TextField
            placeholder="이름"
            aria-label="이름"
            autoComplete="name"
            value={draft.name}
            onChange={(e) => update({ name: e.target.value })}
          />
          <TextField
            placeholder="학부"
            aria-label="학부"
            value={draft.department}
            onChange={(e) => update({ department: e.target.value })}
          />
          <TextField
            placeholder="학번"
            aria-label="학번"
            inputMode="numeric"
            maxLength={10}
            value={draft.studentNumber}
            invalid={studentNumberError !== undefined}
            errorText={studentNumberError}
            onBlur={() => setStudentNumberTouched(true)}
            onChange={(e) => {
              const studentNumber = e.target.value.replace(/\D/g, "").slice(0, 10);
              if (isStudentNumber(studentNumber)) setStudentNumberTouched(false);
              update({ studentNumber });
            }}
          />
        </div>
      </main>

      <footer className="signup__footer">
        <div className="signup__terms student-signup-info__terms">
          <Checkbox
            checked={draft.agreedToTerms}
            onChange={(agreedToTerms) => update({ agreedToTerms })}
            label="이용약관에 모두 동의해요"
          />
          <button type="button" className="signup__terms-view press-text" onClick={() => setTermsOpen(true)}>
            보기
          </button>
        </div>
        <Button
          fullWidth
          tone="student"
          disabled={!complete}
          onClick={handleNext}
        >
          다음
        </Button>
      </footer>

      <TermsSheet open={termsOpen} onClose={() => setTermsOpen(false)} tone="student" />
    </div>
  );
}

export default StudentSignupInfoPage;
