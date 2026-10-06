import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  Button,
  LabelChip,
  LoadNotice,
  RoleAvatar,
  SubScreen,
  TrustChips,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  admissionYearText,
  averageReviewRating,
  jobSpecialtyNames,
  parsePositiveId,
  useApplicantProfile,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatDotDate } from "../lib/date";
import { studentTitle } from "../lib/korean";
import "./OwnerStudentPage.css";

/**
 * 피그마 「지원자 학생 프로필 보기」. GET /jobs/{id}/applications/{applicationId}/profile (ADR 0030).
 * 전공역량 · 자격증 · 포트폴리오 · 후기, 아래에 「이 학생에게 맡기기」(결제 화면).
 * 한 줄 소개 · 마감 지킴은 서버가 intro · onTimeRate 를 줄 때만 보이고, 자격증은 취득 연도만 온다.
 */
function OwnerApplicantProfilePage() {
  const { requestId, applicationId } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.requestApplicants(requestId ?? ""));
  const { load, reload } = useApplicantProfile(
    parsePositiveId(requestId),
    parsePositiveId(applicationId),
  );

  if (load.status === "notFound") return <OwnerMissing title="지원자 프로필" onBack={back} />;
  if (load.status === "closed") {
    return (
      <OwnerMissing title="지원자 프로필" onBack={back} message="취소된 의뢰라 프로필을 볼 수 없어요" />
    );
  }

  const profile = load.status === "loaded" ? load.data : undefined;
  const rating = profile && averageReviewRating(profile);
  const portfolioUrl = profile?.portfolioUrl?.trim();
  const hasPortfolio = (profile?.certificates.length ?? 0) > 0 || !!portfolioUrl;
  const badges = profile ? jobSpecialtyNames(profile.specialtyCategories) : [];

  return (
    <SubScreen
      title="지원자 프로필"
      onBack={back}
      footer={
        profile && (
          <Button
            fullWidth
            onClick={() => navigate(OWNER_PATHS.assign(requestId ?? "", applicationId ?? ""))}
          >
            이 학생에게 맡기기
          </Button>
        )
      }
    >
      {!profile && (
        <LoadNotice
          status={load.status === "loading" ? "loading" : "error"}
          loadingText="프로필을 불러오는 중이에요"
          errorText="프로필을 불러오지 못했어요"
          onRetry={reload}
        />
      )}

      {profile && (
        <div className="owner-student">
          <header className="owner-student__head">
            <RoleAvatar role="student" size={80} />
            <h2 className="owner-student__name">{studentTitle(profile.student.name)}</h2>
            <p className="owner-student__school">
              {[
                profile.student.university ?? "광운대",
                profile.student.major,
                admissionYearText(profile.student.studentNumber),
              ]
                .filter(Boolean)
                .join(" ")}
            </p>
            {profile.intro?.trim() && <p className="owner-student__intro">{profile.intro}</p>}
            <TrustChips proposalCount={profile.proposalCount} noShowCount={profile.penaltyCount} />
          </header>

          <dl className="owner-student__stats">
            <div className="owner-student__stat">
              <dt>완료한 작업</dt>
              <dd>{profile.completedJobCount}건</dd>
            </div>
            <div className="owner-student__stat">
              <dt>마감 지킴</dt>
              <dd>{typeof profile.onTimeRate === "number" ? `${profile.onTimeRate}%` : "-"}</dd>
            </div>
            <div className="owner-student__stat">
              <dt>사장님 평점</dt>
              <dd>{rating === undefined ? "-" : `★ ${rating.toFixed(1)}`}</dd>
            </div>
          </dl>

          <section className="owner-student__section">
            <h3 className="owner-student__section-title">전공역량·특기</h3>
            <div className="owner-student__badges">
              {badges.map((badge) => (
                <LabelChip key={badge} label={badge} />
              ))}
            </div>
          </section>

          <section className="owner-student__section">
            <h3 className="owner-student__section-title">자격증·포트폴리오</h3>
            {hasPortfolio ? (
              <div className="owner-student__box">
                {profile.certificates.map((certificate) => (
                  <div key={certificate.certificateName} className="owner-student__cert">
                    <strong>{certificate.certificateName}</strong>
                    {certificate.acquiredYear && <span>{certificate.acquiredYear}</span>}
                  </div>
                ))}
                {profile.certificates.length > 0 && portfolioUrl && (
                  <div className="owner-student__divider" />
                )}
                {portfolioUrl && (
                  <div className="owner-student__portfolio">
                    <AppImage name="iconLink" width={16} />
                    <span className="owner-student__portfolio-url">{portfolioUrl}</span>
                    <a
                      className="owner-student__open"
                      href={/^https?:\/\//.test(portfolioUrl) ? portfolioUrl : `https://${portfolioUrl}`}
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
              <p className="owner-student__empty">아직 올린 자격증·포트폴리오가 없어요</p>
            )}
          </section>

          <section className="owner-student__section">
            <h3 className="owner-student__section-title">
              사장님 후기 <span>{profile.reviewCount}</span>
            </h3>
            {profile.reviews.length > 0 ? (
              <ul className="owner-student__reviews">
                {profile.reviews.map((review) => (
                  <li key={`${review.storeName}-${review.createdAt}`} className="owner-student__review">
                    <div className="owner-student__review-head">
                      <strong>{review.storeName}</strong>
                      <span className="owner-student__review-work">{review.jobTitle}</span>
                      <span className="owner-student__review-rating">★ {review.rating.toFixed(1)}</span>
                    </div>
                    <p className="owner-student__review-text">{review.content}</p>
                    <p className="owner-student__review-date">{formatDotDate(review.createdAt.slice(0, 10))}</p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="owner-student__empty">아직 받은 후기가 없어요</p>
            )}
          </section>
        </div>
      )}
    </SubScreen>
  );
}

export default OwnerApplicantProfilePage;
