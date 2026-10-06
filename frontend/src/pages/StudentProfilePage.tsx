import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AppImage,
  LabelChip,
  ProfilePhoto,
  SubScreen,
  TextButton,
  TrustChips,
} from "../components";
import {
  STUDENT_PATHS,
  setMyProfilePhoto,
  useMyProfile,
  useMyProfilePhoto,
  useStudentSettlements,
} from "../features/student";
import type { ProfileEditSection } from "../features/student";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { formatDotDate } from "../lib/date";
import { formatWon } from "../lib/money";
import { MAX_SPECIALTY_BADGES } from "../types/specialty";
import "./StudentProfilePage.css";

/** 접혀 있을 때 보여 줄 후기 · 정산 수 */
const PREVIEW_COUNT = 2;
const SETTLEMENT_PREVIEW_COUNT = 3;

/**
 * 피그마 「프로필 수정(학생)」. 사장님이 보는 내 프로필 그대로 보여 주고,
 * 아래에 나만 보는 정산 내역을 붙인다. 각 「수정」은 「프로필 편집(학생)」을 연다.
 */
function StudentProfilePage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.me);
  const profile = useMyProfile();
  const { settlements } = useStudentSettlements();
  const photo = useMyProfilePhoto();
  const photoFiles = useMemo(() => (photo ? [photo] : []), [photo]);
  const [photoUrl] = useObjectUrls(photoFiles);
  const [reviewsExpanded, setReviewsExpanded] = useState(false);

  const openEdit = (section?: ProfileEditSection) =>
    navigate(STUDENT_PATHS.profileEdit, { state: section ? { section } : undefined });

  const reviews = reviewsExpanded ? profile.reviews : profile.reviews.slice(0, PREVIEW_COUNT);
  const settled = settlements
    .filter((s) => s.status !== "expected")
    .slice(0, SETTLEMENT_PREVIEW_COUNT);

  return (
    <SubScreen title="프로필 수정" onBack={back}>
      <div className="student-profile">
        <header className="student-profile__head">
          <ProfilePhoto size="medium" src={photoUrl} onSelect={setMyProfilePhoto} />
          <h2 className="student-profile__name">{profile.name} 학생</h2>
          <p className="student-profile__school">
            광운대 {profile.department} {profile.year}
          </p>
          <p className="student-profile__intro">{profile.intro}</p>
          <TrustChips proposalCount={profile.proposalCount} noShowCount={profile.noShowCount} />
          <button type="button" className="student-profile__edit" onClick={() => openEdit()}>
            기본 정보 수정
          </button>
        </header>

        <dl className="student-profile__stats">
          <div className="student-profile__stat">
            <dt>완료한 작업</dt>
            <dd>{profile.completedCount}건</dd>
          </div>
          <div className="student-profile__stat">
            <dt>사장님 평점</dt>
            <dd>{profile.rating === undefined ? "-" : `★ ${profile.rating.toFixed(1)}`}</dd>
          </div>
        </dl>

        <section className="student-profile__section">
          <div className="student-profile__section-head">
            <h3 className="student-profile__section-title">
              전공역량·특기{" "}
              <span>
                {profile.badges.length}/{MAX_SPECIALTY_BADGES}
              </span>
            </h3>
            <TextButton onClick={() => openEdit("badges")}>수정</TextButton>
          </div>
          <div className="student-profile__badges">
            {profile.badges.map((badge) => (
              <LabelChip key={badge} label={badge} />
            ))}
          </div>
        </section>

        <section className="student-profile__section">
          <div className="student-profile__section-head">
            <h3 className="student-profile__section-title">자격증·포트폴리오</h3>
            <TextButton onClick={() => openEdit("certificates")}>수정</TextButton>
          </div>
          <div className="student-profile__box">
            {profile.certificates.map((certificate) => (
              <div key={certificate.name} className="student-profile__cert">
                <strong>{certificate.name}</strong>
                <span>{certificate.acquiredYear}</span>
              </div>
            ))}
            {profile.certificates.length > 0 && profile.portfolioUrl && (
              <div className="student-profile__divider" />
            )}
            {profile.portfolioUrl && (
              <div className="student-profile__portfolio">
                <AppImage name="iconLink" width={16} />
                <span className="student-profile__portfolio-url">{profile.portfolioUrl}</span>
                <a
                  className="student-profile__open"
                  href={`https://${profile.portfolioUrl}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  열기
                  <AppImage name="iconChevronRight14" />
                </a>
              </div>
            )}
          </div>
        </section>

        <section className="student-profile__section">
          <div className="student-profile__section-head">
            <h3 className="student-profile__section-title">
              받은 후기 <span>{profile.reviews.length}</span>
            </h3>
            {profile.reviews.length > PREVIEW_COUNT && (
              <TextButton onClick={() => setReviewsExpanded((v) => !v)}>
                {reviewsExpanded ? "접기" : "전체 보기"}
              </TextButton>
            )}
          </div>
          {reviews.length > 0 ? (
            <ul className="student-profile__reviews">
              {reviews.map((review) => (
                <li key={review.workId} className="student-profile__review">
                  <div className="student-profile__review-head">
                    <strong>{review.storeName}</strong>
                    <span className="student-profile__review-work">{review.workTitle}</span>
                    <span className="student-profile__review-rating">★ {review.rating.toFixed(1)}</span>
                  </div>
                  <p className="student-profile__review-text">{review.text}</p>
                  <p className="student-profile__review-date">{formatDotDate(review.date)}</p>
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
          {settled.length > 0 ? (
            <ul className="student-profile__box student-profile__settlements">
              {settled.map((s) => (
                <li key={s.workId} className="student-profile__settlement">
                  <span>
                    <strong>{s.title}</strong>
                    <small>{formatDotDate(s.date)}</small>
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
