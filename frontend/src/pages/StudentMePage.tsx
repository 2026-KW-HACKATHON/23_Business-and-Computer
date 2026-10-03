import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { MenuList, ProfilePhoto, SubScreen, SummaryCard } from "../components";
import { clearTokens } from "../features/auth";
import {
  STUDENT_PATHS,
  useMyProfile,
  useMyProposals,
  useStudentApplications,
  useStudentWorks,
} from "../features/student";
import type { StudentActivityTab } from "../features/student";
import { TermsSheet } from "../features/signup";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import "./StudentMePage.css";

const ACTIVITY_TABS: StudentActivityTab[] = ["applied", "proposals", "inProgress", "done"];

/** 피그마 「내 정보 · 설정 (학생)」. 프로필 · 요약 · 내 활동 · 설정 · 로그아웃 */
function StudentMePage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const profile = useMyProfile();
  const applications = useStudentApplications();
  const proposals = useMyProposals();
  const works = useStudentWorks();
  // 사진 업로드는 백엔드 연동 전까지 이 화면에서 미리보기만 한다
  const [photo, setPhoto] = useState<File | null>(null);
  const photoFiles = useMemo(() => (photo ? [photo] : []), [photo]);
  const [photoUrl] = useObjectUrls(photoFiles);
  const [termsOpen, setTermsOpen] = useState(false);

  const inProgress = works.filter((w) => ["drafting", "revising", "submitted"].includes(w.status));
  const counts = [applications.length, proposals.length, inProgress.length, profile.completedCount];
  const openActivity = (tab: StudentActivityTab) => navigate(STUDENT_PATHS.activity(tab));

  const logout = () => {
    clearTokens();
    navigate("/login", { replace: true });
  };

  return (
    <SubScreen title="내 정보" onBack={back}>
      <section className="student-me__profile">
        <div className="student-me__head">
          <ProfilePhoto size="small" src={photoUrl} onSelect={setPhoto} />
          <div className="student-me__info">
            <h2 className="student-me__name">{profile.name} 학생</h2>
            <p className="student-me__school">
              광운대학교 {profile.department} {profile.year}
            </p>
            <p className="student-me__fields">{profile.fields.join(" / ")}</p>
          </div>
        </div>
        <div className="student-me__badges">
          <span className="student-me__verified">
            <span className="student-me__verified-check" aria-hidden="true">
              <svg viewBox="0 0 10 10" fill="none">
                <path
                  d="m2 5.2 2 2L8 3"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </span>
            광운대 인증 완료
          </span>
          <button
            type="button"
            className="student-me__edit"
            onClick={() => navigate(STUDENT_PATHS.profile)}
          >
            프로필 수정
          </button>
        </div>
        <SummaryCard
          items={["지원한 의뢰", "보낸 제안", "진행 중", "완료"].map((label, i) => ({
            label,
            count: counts[i],
          }))}
          onSelect={(i) => openActivity(ACTIVITY_TABS[i])}
        />
      </section>

      <section className="student-me__section">
        <h2 className="student-me__section-title">내 활동</h2>
        <MenuList
          items={[
            { label: "지원한 의뢰", onClick: () => openActivity("applied") },
            { label: "보낸 제안", onClick: () => openActivity("proposals") },
            { label: "진행 중", onClick: () => openActivity("inProgress") },
            { label: "완료 및 정산 내역", onClick: () => openActivity("done") },
            { label: "내 작업물 모아보기", onClick: () => navigate(STUDENT_PATHS.portfolio) },
          ]}
        />
      </section>

      <section className="student-me__section student-me__section--settings">
        <div className="student-me__group">
          <h2 className="student-me__section-title">설정</h2>
          <MenuList
            items={[
              { label: "알림 설정" },
              { label: "약관 및 정책", onClick: () => setTermsOpen(true) },
            ]}
          />
        </div>
        <MenuList items={[{ label: "로그아웃", danger: true, onClick: logout }]} />
      </section>

      <TermsSheet open={termsOpen} onClose={() => setTermsOpen(false)} tone="student" />
    </SubScreen>
  );
}

export default StudentMePage;
