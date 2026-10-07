import { useLocation, useNavigate, useParams } from "react-router-dom";
import { AppImage, Button, LabelChip, LoadNotice, RoleAvatar, SubScreen, TrustChips } from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  parsePositiveId,
  useOwnerRequest,
  useOwnerStudentProfile,
  useStudentProfile,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatDotDate } from "../lib/date";
import OwnerStudentProfileView from "./OwnerStudentProfileView";
import "./OwnerStudentPage.css";

/** 학생 프로필 (/owner/students/:id). 주소의 id 가 숫자면 서버 학생(studentProfileId), 아니면 샘플 학생 */
function OwnerStudentPage() {
  const { studentId = "" } = useParams();
  const profileId = parsePositiveId(studentId);
  return profileId === undefined ? <SampleStudent studentId={studentId} /> : <JobStudent profileId={profileId} />;
}

/**
 * 서버 학생 프로필. GET /students/{id}/profile (ADR 0038). 진행 중 작업 · 받은 제안의 「프로필 보기」가 연다.
 * 본문은 지원자 프로필과 같은 OwnerStudentProfileView 이고, 아래 버튼은 없다.
 */
function JobStudent({ profileId }: { profileId: number }) {
  const back = useBack(OWNER_PATHS.home);
  const { load, reload } = useOwnerStudentProfile(profileId);

  if (load.status === "notFound") {
    return <OwnerMissing title="학생 프로필" onBack={back} message="찾는 학생이 없어요" />;
  }
  return (
    <SubScreen title="학생 프로필" onBack={back}>
      {load.status === "loaded" ? (
        <OwnerStudentProfileView profile={load.data} />
      ) : (
        <LoadNotice
          status={load.status === "loading" ? "loading" : "error"}
          loadingText="프로필을 불러오는 중이에요"
          errorText="프로필을 불러오지 못했어요"
          onRetry={reload}
        />
      )}
    </SubScreen>
  );
}

/**
 * 샘플 학생 (피그마 「지원자 학생 프로필 보기」). 뱃지 · 자격증 · 후기 (작업계획서는 없다).
 * 지원자 목록에서 들어오면 (router state 의 requestId) 아래에 「이 학생에게 맡기기」.
 */
function SampleStudent({ studentId }: { studentId: string }) {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(OWNER_PATHS.home);
  const profile = useStudentProfile(studentId);
  const requestId = (location.state as { requestId?: string } | null)?.requestId ?? "";
  const request = useOwnerRequest(requestId);

  const fromApplicants = request?.applicants.some((a) => a.student.id === studentId) ?? false;
  const title = fromApplicants ? "지원자 프로필" : "학생 프로필";

  if (!profile) return <OwnerMissing title={title} onBack={back} />;

  const hasPortfolio = profile.certificates.length > 0 || profile.portfolioUrl !== undefined;

  return (
    <SubScreen
      title={title}
      onBack={back}
      footer={
        fromApplicants &&
        request && (
          <Button fullWidth onClick={() => navigate(OWNER_PATHS.assign(request.id, profile.id))}>
            이 학생에게 맡기기
          </Button>
        )
      }
    >
      <div className="owner-student">
        <header className="owner-student__head">
          <RoleAvatar role="student" size={80} />
          <h2 className="owner-student__name">{profile.name} 학생</h2>
          <p className="owner-student__school">
            광운대 {profile.department} {profile.year}
          </p>
          <p className="owner-student__intro">{profile.intro}</p>
          <TrustChips proposalCount={profile.proposalCount} noShowCount={profile.noShowCount} />
        </header>

        <dl className="owner-student__stats">
          <div className="owner-student__stat">
            <dt>완료한 작업</dt>
            <dd>{profile.completedCount}건</dd>
          </div>
          <div className="owner-student__stat">
            <dt>사장님 평점</dt>
            <dd>{profile.rating === undefined ? "-" : `★ ${profile.rating.toFixed(1)}`}</dd>
          </div>
        </dl>

        <section className="owner-student__section">
          <h3 className="owner-student__section-title">전공역량·특기</h3>
          <div className="owner-student__badges">
            {profile.badges.map((badge) => (
              <LabelChip key={badge} label={badge} />
            ))}
          </div>
        </section>

        <section className="owner-student__section">
          <h3 className="owner-student__section-title">자격증·포트폴리오</h3>
          {hasPortfolio ? (
            <div className="owner-student__box">
              {profile.certificates.map((certificate) => (
                <div key={certificate.name} className="owner-student__cert">
                  <strong>{certificate.name}</strong>
                  <span>{certificate.acquiredYear}</span>
                </div>
              ))}
              {profile.certificates.length > 0 && profile.portfolioUrl && (
                <div className="owner-student__divider" />
              )}
              {profile.portfolioUrl && (
                <div className="owner-student__portfolio">
                  <AppImage name="iconLink" width={16} />
                  <span className="owner-student__portfolio-url">{profile.portfolioUrl}</span>
                  <a
                    className="owner-student__open"
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
          ) : (
            <p className="owner-student__empty">아직 올린 자격증·포트폴리오가 없어요</p>
          )}
        </section>

        <section className="owner-student__section">
          <h3 className="owner-student__section-title">
            사장님 후기 <span>{profile.reviews.length}</span>
          </h3>
          {profile.reviews.length > 0 ? (
            <ul className="owner-student__reviews">
              {profile.reviews.map((review) => (
                <li key={`${review.storeName}-${review.date}`} className="owner-student__review">
                  <div className="owner-student__review-head">
                    <strong>{review.storeName}</strong>
                    <span className="owner-student__review-work">{review.workTitle}</span>
                    <span className="owner-student__review-rating">
                      ★ {review.rating.toFixed(1)}
                    </span>
                  </div>
                  <p className="owner-student__review-text">{review.text}</p>
                  <p className="owner-student__review-date">{formatDotDate(review.date)}</p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="owner-student__empty">아직 받은 후기가 없어요</p>
          )}
        </section>
      </div>
    </SubScreen>
  );
}

export default OwnerStudentPage;
