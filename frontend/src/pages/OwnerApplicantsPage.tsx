import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  FlowBar,
  InfoRows,
  LabelChip,
  LoadNotice,
  RoleAvatar,
  SubScreen,
  TextButton,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  admissionYearText,
  applicantPlan,
  flowSteps,
  jobCategoryNames,
  jobSpecialtyNames,
  parsePositiveId,
  proposalStudentRecord,
  useJobApplications,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDayWeekday } from "../lib/date";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";
import "./OwnerDetailPage.css";
import "./OwnerApplicantsPage.css";

/**
 * 피그마 「보낸 의뢰 상세 · 지원자 목록」. GET /jobs/{id}/applications 의 지원서(한 줄 요약 · 작업계획서 ·
 * 결과물)를 보고 맡길 학생을 고른다 (ADR 0030). 「이 학생에게 맡기기」는 의뢰 id · 지원서 id 로 결제 화면에 간다.
 * 모집이 끝난 의뢰(409)는 지원자를 볼 수 없다고 알린다.
 */
function OwnerApplicantsPage() {
  const { requestId } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const jobId = parsePositiveId(requestId);
  const { load, reload } = useJobApplications(jobId, "LATEST");

  if (load.status === "notFound") return <OwnerMissing title="지원자 목록" onBack={back} />;
  if (load.status === "closed") {
    return (
      <OwnerMissing title="지원자 목록" onBack={back} message="모집이 끝나 지원자를 볼 수 없어요" />
    );
  }

  const data = load.status === "loaded" ? load.data : undefined;
  const id = String(jobId ?? "");

  return (
    <SubScreen title="지원자 목록" onBack={back}>
      {!data && (
        <LoadNotice
          layout="cards"
          status={load.status === "loading" ? "loading" : "error"}
          loadingText="지원자를 불러오는 중이에요"
          errorText="지원자를 불러오지 못했어요"
          onRetry={reload}
        />
      )}

      {data && (
        <div className="owner-detail owner-detail--tight">
          <div className="owner-applicants__summary">
            <div className="owner-applicants__summary-head">
              <WorkKindIcon kind="request" />
              <h2 className="owner-applicants__summary-title">{data.job.title}</h2>
            </div>
            <div className="owner-applicants__badges">
              {jobCategoryNames(data.job.specialtyCategories).map((name) => (
                <CategoryBadge key={name} field={name} />
              ))}
            </div>
            <InfoRows
              rows={[
                { label: "예산", value: formatWon(data.job.budget) },
                { label: "초안 마감", value: formatMonthDayWeekday(data.job.draftDeadline) },
                { label: "최종 마감", value: formatMonthDayWeekday(data.job.finalDeadline) },
              ]}
            />
            <div className="owner-applicants__divider" />
            <TextButton onClick={() => navigate(OWNER_PATHS.request(id))}>의뢰서 전체 보기</TextButton>
          </div>

          <FlowBar steps={flowSteps("의뢰", 0, "학생 고르기")} />

          <div className="owner-applicants__head">
            <div className="owner-applicants__title-row">
              <h2 className="owner-applicants__title">
                지원자 <span>{data.applicantCount}</span>
              </h2>
              <TextButton showFilter showChevron={false}>
                추천순
              </TextButton>
            </div>
            <p className="owner-applicants__description">
              전공·지원서·후기를 보고 맡길 학생을 골라 주세요
            </p>
          </div>

          {data.applicants.length === 0 ? (
            <p className="owner-applicants__empty">아직 지원한 학생이 없어요</p>
          ) : (
            <ul className="owner-applicants__list">
              {data.applicants.map((applicant) => {
                const applicationId = String(applicant.jobApplicationId);
                return (
                  <li key={applicant.jobApplicationId} className="owner-applicants__card">
                    <div className="owner-applicants__student">
                      <RoleAvatar role="student" />
                      <div className="owner-applicants__student-info">
                        <span className="owner-applicants__name-row">
                          <strong>{studentTitle(applicant.name)}</strong>
                          <span>{admissionYearText(applicant.studentNumber)}</span>
                        </span>
                        <span className="owner-applicants__department">{applicant.major}</span>
                      </div>
                      <span
                        className={`owner-applicants__record${
                          applicant.completedJobCount === 0 ? " owner-applicants__record--new" : ""
                        }`}
                      >
                        {proposalStudentRecord(applicant)}
                      </span>
                    </div>
                    <div className="owner-applicants__badges">
                      {jobSpecialtyNames(applicant.specialtyCategories).map((badge) => (
                        <LabelChip key={badge} label={badge} />
                      ))}
                    </div>
                    <WorkPlan plan={applicantPlan(applicant)} collapsible />
                    <div className="owner-applicants__actions">
                      <Button
                        variant="secondary"
                        onClick={() => navigate(OWNER_PATHS.applicantProfile(id, applicationId))}
                      >
                        프로필 보기
                      </Button>
                      <Button onClick={() => navigate(OWNER_PATHS.assign(id, applicationId))}>
                        이 학생에게 맡기기
                      </Button>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </SubScreen>
  );
}

export default OwnerApplicantsPage;
