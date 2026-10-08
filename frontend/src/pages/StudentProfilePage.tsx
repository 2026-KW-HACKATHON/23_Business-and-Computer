import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AppImage,
  LabelChip,
  LoadNotice,
  ProfilePhoto,
  SubScreen,
  TextButton,
  TrustChips,
} from "../components";
import {
  STUDENT_PATHS,
  portfolioHref,
  portfolioLabel,
  reviewWorkText,
  specialtyNamesOf,
  studentYearText,
  useStudentMe,
  useStudentPhotoChange,
} from "../features/student";
import type { ProfileEditSection, StudentMe } from "../features/student";
import { PROFILE_PHOTO_ACCEPT } from "../features/signup";
import { useBack } from "../hooks/useBack";
import { formatDotDate } from "../lib/date";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";
import { MAX_SPECIALTY_BADGES } from "../types/specialty";
import "./StudentProfilePage.css";

/** 접혀 있을 때 보여 줄 후기 수 */
const PREVIEW_COUNT = 2;

/**
 * 피그마 「프로필 수정(학생)」. 사장님이 보는 내 프로필 그대로 보여 주고,
 * 아래에 나만 보는 정산 내역을 붙인다. 각 「수정」은 「프로필 편집(학생)」을 연다.
 * 모두 GET /students/me (ADR 0041). 받은 후기는 서버가 모두 주고 두 개까지 보이다가 「전체 보기」로 펼친다.
 * 정산은 서버가 주는 최근 세 개까지.
 */
function StudentProfilePage() {
  const back = useBack(STUDENT_PATHS.me);
  const { load, reload } = useStudentMe();

  if (load.status === "loaded") return <ProfileBody me={load.data} onBack={back} />;
  return (
    <SubScreen title="프로필 수정" onBack={back}>
      <LoadNotice
        layout="page"
        status={load.status}
        loadingText="프로필을 불러오는 중이에요"
        errorText="프로필을 불러오지 못했어요"
        onRetry={reload}
      />
    </SubScreen>
  );
}

function ProfileBody({ me, onBack }: { me: StudentMe; onBack: () => void }) {
  const navigate = useNavigate();
  const { photoUrl, changePhoto } = useStudentPhotoChange(me);
  const [reviewsExpanded, setReviewsExpanded] = useState(false);

  const openEdit = (section?: ProfileEditSection) =>
    navigate(STUDENT_PATHS.profileEdit, { state: section ? { section } : undefined });

  const school = ["광운대", me.major?.trim(), studentYearText(me.studentNumber)].filter(Boolean).join(" ");
  const intro = me.introduction?.trim();
  const badges = specialtyNamesOf(me.specialtyCategories);
  const portfolioUrl = me.portfolioUrl?.trim();
  const rating = me.reviewCount > 0 && me.averageRating != null ? Number(me.averageRating) : undefined;
  const reviews = reviewsExpanded ? me.reviews : me.reviews.slice(0, PREVIEW_COUNT);

  return (
    <SubScreen title="프로필 수정" onBack={onBack}>
      <div className="student-profile">
        <header className="student-profile__head">
          <ProfilePhoto size="medium" src={photoUrl} accept={PROFILE_PHOTO_ACCEPT} onSelect={changePhoto} />
          <h2 className="student-profile__name">{studentTitle(me.name)}</h2>
          <p className="student-profile__school">{school}</p>
          {intro && <p className="student-profile__intro">{intro}</p>}
          <TrustChips proposalCount={me.proposalCount} noShowCount={me.penaltyCount} />
          <button type="button" className="student-profile__edit" onClick={() => openEdit()}>
            기본 정보 수정
          </button>
        </header>

        <dl className="student-profile__stats">
          <div className="student-profile__stat">
            <dt>완료한 작업</dt>
            <dd>{me.completedJobCount}건</dd>
          </div>
          <div className="student-profile__stat">
            <dt>사장님 평점</dt>
            <dd>{rating === undefined ? "-" : `★ ${rating.toFixed(1)}`}</dd>
          </div>
        </dl>

        <section className="student-profile__section">
          <div className="student-profile__section-head">
            <h3 className="student-profile__section-title">
              전공역량·특기{" "}
              <span>
                {badges.length}/{MAX_SPECIALTY_BADGES}
              </span>
            </h3>
            <TextButton onClick={() => openEdit("badges")}>수정</TextButton>
          </div>
          <div className="student-profile__badges">
            {badges.map((badge) => (
              <LabelChip key={badge} label={badge} />
            ))}
          </div>
        </section>

        <section className="student-profile__section">
          <div className="student-profile__section-head">
            <h3 className="student-profile__section-title">자격증·포트폴리오</h3>
            <TextButton onClick={() => openEdit("certificates")}>수정</TextButton>
          </div>
          {me.certificates.length > 0 || portfolioUrl ? (
            <div className="student-profile__box">
              {me.certificates.map((certificate) => (
                <div key={certificate.certificateName} className="student-profile__cert">
                  <strong>{certificate.certificateName}</strong>
                  <span>{certificate.acquiredYear}</span>
                </div>
              ))}
              {me.certificates.length > 0 && portfolioUrl && <div className="student-profile__divider" />}
              {portfolioUrl && (
                <div className="student-profile__portfolio">
                  <AppImage name="iconLink" width={16} />
                  <span className="student-profile__portfolio-url">{portfolioLabel(portfolioUrl)}</span>
                  <a
                    className="student-profile__open"
                    href={portfolioHref(portfolioUrl)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    열기
                    <AppImage name="iconChevronRight14" />
                  </a>
                </div>
              )}
            </div>
          ) : (
            <p className="student-profile__empty">아직 올린 자격증·포트폴리오가 없어요</p>
          )}
        </section>

        <section className="student-profile__section">
          <div className="student-profile__section-head">
            <h3 className="student-profile__section-title">
              받은 후기 <span>{me.reviewCount}</span>
            </h3>
            {me.reviews.length > PREVIEW_COUNT && (
              <TextButton onClick={() => setReviewsExpanded((v) => !v)}>
                {reviewsExpanded ? "접기" : "전체 보기"}
              </TextButton>
            )}
          </div>
          {reviews.length > 0 ? (
            <ul className="student-profile__reviews">
              {reviews.map((review, i) => (
                <li key={`${review.createdAt}-${i}`} className="student-profile__review">
                  <div className="student-profile__review-head">
                    <strong>{review.storeName?.trim() || "가게"}</strong>
                    <span className="student-profile__review-work">{reviewWorkText(review)}</span>
                    <span className="student-profile__review-rating">★ {review.rating.toFixed(1)}</span>
                  </div>
                  {review.content?.trim() && (
                    <p className="student-profile__review-text">{review.content.trim()}</p>
                  )}
                  <p className="student-profile__review-date">{formatDotDate(review.createdAt)}</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="student-profile__empty">아직 받은 후기가 없어요</p>
          )}
        </section>

        <section className="student-profile__section">
          <div className="student-profile__section-head">
            <h3 className="student-profile__section-title">
              정산 내역 <small>나만 볼 수 있어요</small>
            </h3>
            <TextButton onClick={() => navigate(STUDENT_PATHS.settlements)}>전체 보기</TextButton>
          </div>
          {me.settlements.length > 0 ? (
            <ul className="student-profile__box student-profile__settlements">
              {me.settlements.map((s) => (
                <li key={s.jobId} className="student-profile__settlement">
                  <span>
                    <strong>{s.title}</strong>
                    {s.settledDate && <small>{formatDotDate(s.settledDate)}</small>}
                  </span>
                  <b>{formatWon(s.amount)}</b>
                </li>
              ))}
            </ul>
          ) : (
            <p className="student-profile__empty">아직 정산된 작업비가 없어요</p>
          )}
        </section>
      </div>
    </SubScreen>
  );
}

export default StudentProfilePage;
