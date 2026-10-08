import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { LoadNotice, MenuList, ProfilePhoto, SubScreen, SummaryCard } from "../components";
import { clearTokens } from "../features/auth";
import {
  STUDENT_PATHS,
  studentYearText,
  useAppliedJobs,
  useProgressJobs,
  useSentProposals,
  useStudentMe,
  useStudentPhotoChange,
} from "../features/student";
import type { StudentActivityTab, StudentMe } from "../features/student";
import { PROFILE_PHOTO_ACCEPT, TermsSheet } from "../features/signup";
import { useBack } from "../hooks/useBack";
import { studentTitle } from "../lib/korean";
import "./StudentMePage.css";

const ACTIVITY_TABS: StudentActivityTab[] = ["applied", "proposals", "inProgress", "done"];

/**
 * 피그마 「내 정보 · 설정 (학생)」. 프로필 · 요약 · 내 활동 · 설정 · 로그아웃.
 * 프로필과 완료 수는 GET /students/me (ADR 0041), 지원한 의뢰 · 보낸 제안 · 진행 중 수는 각 목록
 * (ADR 0027 · 0023 · 0032). 불러오는 중이거나 실패하면 프로필 자리에 안내, 개수는 「-」.
 * 사진을 고르면 바로 올리고(PROFILE) PUT /students/me 로 저장한다.
 */
function StudentMePage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const { load, reload } = useStudentMe();
  const me = load.status === "loaded" ? load.data : undefined;
  const { load: appliedLoad } = useAppliedJobs();
  const { load: proposalsLoad } = useSentProposals();
  const { load: progressLoad } = useProgressJobs();
  const [termsOpen, setTermsOpen] = useState(false);

  const counts = [
    appliedLoad.status === "loaded" ? appliedLoad.jobs.length : "-",
    proposalsLoad.status === "loaded" ? proposalsLoad.proposals.length : "-",
    progressLoad.status === "loaded" ? progressLoad.jobs.length : "-",
    me ? me.completedJobCount : "-",
  ];
  const openActivity = (tab: StudentActivityTab) => navigate(STUDENT_PATHS.activity(tab));

  const logout = () => {
    clearTokens();
    navigate("/login", { replace: true });
  };

  return (
    <SubScreen title="내 정보" onBack={back}>
      <section className="student-me__profile">
        {me ? (
          <MeHead me={me} />
        ) : (
          <LoadNotice
            status={load.status === "loading" ? "loading" : "error"}
            loadingText="내 정보를 불러오는 중이에요"
            errorText="내 정보를 불러오지 못했어요"
            onRetry={reload}
          />
        )}
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

/**
 * 사진 · 이름 · 학교와 학번 · 특기 대분류, 「광운대 인증 완료」 · 「프로필 수정」.
 * 학교 줄은 「광운대학교 경영학부 24학번」 (학과가 없으면 빼고).
 */
function MeHead({ me }: { me: StudentMe }) {
  const navigate = useNavigate();
  const { photoUrl, changePhoto } = useStudentPhotoChange(me);
  const school = [me.university, me.major?.trim(), studentYearText(me.studentNumber)].filter(Boolean).join(" ");
  const fields = me.specialtyCategories.map((category) => category.name).join(" / ");

  return (
    <>
      <div className="student-me__head">
        <ProfilePhoto size="small" src={photoUrl} accept={PROFILE_PHOTO_ACCEPT} onSelect={changePhoto} />
        <div className="student-me__info">
          <h2 className="student-me__name">{studentTitle(me.name)}</h2>
          <p className="student-me__school">{school}</p>
          {fields && <p className="student-me__fields">{fields}</p>}
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
    </>
  );
}

export default StudentMePage;
