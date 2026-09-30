import { useMemo, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AppBar, AppImage, Button, Chip, ProfilePhoto, StepIndicator, TextField } from "../components";
import { useStudentSignup } from "../features/signup";
import type { Certificate } from "../features/signup";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { MAX_SPECIALTY_BADGES, SPECIALTY_BADGES } from "../types/specialty";
import "./SignupPage.css";
import "./StudentSignupProfilePage.css";

/** 학번 앞 두 자리로 「24학번」을 만든다 (예: 2024402145 → 24학번) */
function admissionYear(studentNumber: string): string {
  return studentNumber.length >= 4 ? `${studentNumber.slice(2, 4)}학번` : `${studentNumber}학번`;
}

/** 피그마 「회원가입 - 프로필 입력(학생) 3/3」. 본문만 스크롤하고 아래 버튼은 고정 */
function StudentSignupProfilePage() {
  const navigate = useNavigate();
  const { draft, update } = useStudentSignup();
  const [limitReached, setLimitReached] = useState(false);
  const profileFiles = useMemo(
    () => (draft.profilePhoto ? [draft.profilePhoto] : []),
    [draft.profilePhoto],
  );
  const [profileUrl] = useObjectUrls(profileFiles);

  // 메일 인증을 마치지 않고 들어오면(새로고침 포함) 역할 선택부터 다시
  if (draft.verifiedEmail === "") return <Navigate to="/signup/role" replace />;

  const toggleBadge = (badge: string) => {
    if (draft.badges.includes(badge)) {
      update({ badges: draft.badges.filter((b) => b !== badge) });
      setLimitReached(false);
      return;
    }
    // 이미 5개면 고르지 않고 안내 문구로 알린다
    if (draft.badges.length >= MAX_SPECIALTY_BADGES) {
      setLimitReached(true);
      return;
    }
    update({ badges: [...draft.badges, badge] });
  };

  const editCertificate = (index: number, patch: Partial<Certificate>) => {
    update({
      certificates: draft.certificates.map((c, i) => (i === index ? { ...c, ...patch } : c)),
    });
  };

  // 백엔드 연동 전: 가입 저장 없이 완료 화면으로 간다. 뒤로 가기로 돌아오지 않게 교체한다.
  const handleComplete = () => {
    update({ completed: true });
    navigate("/signup/student/done", { replace: true });
  };

  return (
    <div className="signup">
      <AppBar
        title="프로필 입력"
        onBack={() => navigate("/signup/student/2")}
        muted
        bottom={<StepIndicator total={3} current={3} tone="student" />}
      />

      <main className="signup__body student-signup-profile__body">
        <div className="student-signup-profile__head">
          <ProfilePhoto src={profileUrl} onSelect={(profilePhoto) => update({ profilePhoto })} />
          <div className="student-signup-profile__who">
            <strong className="student-signup-profile__name">{draft.name.trim()} 학생</strong>
            <span className="student-signup-profile__school">
              광운대학교 {draft.department.trim()} {admissionYear(draft.studentNumber)}
            </span>
          </div>
        </div>

        <TextField
          className="student-signup-profile__intro"
          placeholder="나를 표현하는 한 줄 소개"
          aria-label="한 줄 소개"
          value={draft.intro}
          onChange={(e) => update({ intro: e.target.value })}
        />

        <h2 className="student-signup-profile__title">전공 역량 및 특기</h2>

        <label className="student-signup-profile__portfolio">
          <span className="student-signup-profile__portfolio-title">외부 포트폴리오 추가</span>
          <span className="student-signup-profile__portfolio-link">
            <AppImage name="iconLink" alt="" />
            <input
              type="url"
              inputMode="url"
              placeholder="링크를 입력해주세요"
              value={draft.portfolioUrl}
              onChange={(e) => update({ portfolioUrl: e.target.value })}
            />
          </span>
        </label>

        <section className="student-signup-profile__panel" aria-labelledby="specialty-title">
          <div className="student-signup-profile__panel-head">
            <h3 id="specialty-title" className="student-signup-profile__panel-title">
              나의 전공역량 및 특기
            </h3>
            <span className="student-signup-profile__count" aria-live="polite">
              {draft.badges.length}/{MAX_SPECIALTY_BADGES}
            </span>
          </div>
          <p
            className={`student-signup-profile__guide${limitReached ? " student-signup-profile__guide--limit" : ""}`}
            role={limitReached ? "alert" : undefined}
          >
            {limitReached
              ? "최대 5개까지 고를 수 있어요"
              : "해당하는 뱃지를 눌러 골라 주세요 (최대 5개)"}
          </p>

          <div className="student-signup-profile__groups">
            {SPECIALTY_BADGES.map(({ field, badges }) => (
              <div key={field} className="student-signup-profile__group">
                <h4 className="student-signup-profile__group-title">{field}</h4>
                <div className="student-signup-profile__chips">
                  {badges.map((badge) => (
                    <Chip
                      key={badge}
                      variant="outlined"
                      label={badge}
                      selected={draft.badges.includes(badge)}
                      onClick={() => toggleBadge(badge)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>

          <div className="student-signup-profile__certs">
            <div>
              <h3 className="student-signup-profile__certs-title">보유 자격증 (선택)</h3>
              <p className="student-signup-profile__certs-guide">자격증 이름과 취득한 연월을 적어 주세요</p>
            </div>
            {draft.certificates.map((certificate, i) => (
              <div key={i} className="student-signup-profile__cert-row">
                <input
                  className="student-signup-profile__cert-input"
                  placeholder="자격증명"
                  aria-label={`자격증 ${i + 1} 이름`}
                  value={certificate.name}
                  onChange={(e) => editCertificate(i, { name: e.target.value })}
                />
                <input
                  className="student-signup-profile__cert-input student-signup-profile__cert-input--date"
                  placeholder="취득 연월"
                  aria-label={`자격증 ${i + 1} 취득 연월`}
                  value={certificate.acquiredAt}
                  onChange={(e) => editCertificate(i, { acquiredAt: e.target.value })}
                />
              </div>
            ))}
            <button
              type="button"
              className="student-signup-profile__cert-add"
              onClick={() => update({ certificates: [...draft.certificates, { name: "", acquiredAt: "" }] })}
            >
              + 자격증 추가
            </button>
          </div>
        </section>
      </main>

      <footer className="signup__footer student-signup-profile__footer">
        <Button fullWidth tone="student" disabled={draft.badges.length === 0} onClick={handleComplete}>
          회원가입 완료
        </Button>
      </footer>
    </div>
  );
}

export default StudentSignupProfilePage;
