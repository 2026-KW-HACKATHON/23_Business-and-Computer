import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  Button,
  FlowBar,
  InfoRows,
  LoadNotice,
  NumberedSteps,
  RoleAvatar,
  SubScreen,
  TrustChips,
  WorkPlan,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  applicantPlan,
  flowSteps,
  parsePositiveId,
  proposalStudentRecord,
  studentMetaText,
  useApplicantProfile,
  useJobAssignment,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDayWeekday } from "../lib/date";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";
import "./OwnerAssignPage.css";

/**
 * 피그마 「이 학생에게 맡기기」. 맡길 학생 · 작업계획서 · 의뢰 조건을 한 번 더 확인한다.
 * 의뢰와 지원자는 GET /jobs/{id}/applications 에서 지원서 id 로 고르고, 수정 횟수는
 * GET /me/jobs?status=OPEN, 제안 · 패널티 횟수는 지원자 프로필에서 채운다 (ADR 0037).
 */
function OwnerAssignPage() {
  const { requestId = "", applicationId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.requestApplicants(requestId));
  const jobId = parsePositiveId(requestId);
  const applicationNumber = parsePositiveId(applicationId);
  const { load, reload } = useJobAssignment(jobId, applicationNumber);
  const { load: profileLoad } = useApplicantProfile(jobId, applicationNumber);

  if (load.status === "notFound") return <OwnerMissing title="이 학생에게 맡기기" onBack={back} />;
  if (load.status === "closed") {
    return (
      <OwnerMissing title="이 학생에게 맡기기" onBack={back} message="모집이 끝나 학생을 고를 수 없어요" />
    );
  }
  if (load.status !== "loaded") {
    return (
      <SubScreen title="이 학생에게 맡기기" onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="지원자를 불러오는 중이에요"
          errorText="지원자를 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }

  const { job, applicant, revisionCount } = load.data;
  const name = studentTitle(applicant.name);
  const meta = [studentMetaText(applicant.studentNumber, applicant.major), proposalStudentRecord(applicant)]
    .filter(Boolean)
    .join("\n");
  const profile = profileLoad.status === "loaded" ? profileLoad.data : undefined;

  return (
    <SubScreen
      title="이 학생에게 맡기기"
      onBack={back}
      footer={
        <div className="owner-assign__actions">
          <Button variant="secondary" onClick={back}>
            더 볼게요
          </Button>
          <Button onClick={() => navigate(OWNER_PATHS.assignPay(requestId, applicationId))}>
            네, 맡길게요
          </Button>
        </div>
      }
    >
      <div className="owner-assign">
        <FlowBar steps={flowSteps("의뢰", 1, "결제 후 시작")} />

        <div className="owner-assign__intro">
          <h2 className="owner-assign__title">{`${name}에게\n이 의뢰를 맡길까요?`}</h2>
        </div>

        <section className="owner-assign__card">
          <div className="owner-assign__student">
            <RoleAvatar role="student" size={48} />
            <div className="owner-assign__student-info">
              <strong className="owner-assign__name">{name}</strong>
              <span className="owner-assign__meta">{meta}</span>
            </div>
          </div>
          {profile && (
            <TrustChips proposalCount={profile.proposalCount} noShowCount={profile.penaltyCount} />
          )}
          <WorkPlan plan={applicantPlan(applicant)} />
        </section>

        <section className="owner-assign__card">
          <div className="owner-assign__request-head">
            <AppImage name="iconCardRequest" />
            <h3 className="owner-assign__request-title">{job.title}</h3>
          </div>
          <InfoRows
            rows={[
              { label: "작업비", value: formatWon(job.budget) },
              { label: "초안 마감", value: formatMonthDayWeekday(job.draftDeadline) },
              { label: "최종 마감", value: formatMonthDayWeekday(job.finalDeadline) },
              ...(revisionCount !== undefined ? [{ label: "수정", value: `${revisionCount}회` }] : []),
            ]}
          />
        </section>

        <section className="owner-assign__process">
          <h3 className="owner-assign__process-title">맡기면 이렇게 진행돼요</h3>
          <NumberedSteps
            steps={[
              {
                title: `작업비 ${formatWon(job.budget)}을 안전결제로 맡겨요`,
                description: "골목인턴이 보관하고, 완료를 확인하면 학생에게 보내요",
              },
              { title: "결제가 끝나면 바로 작업이 시작되고 채팅방이 열려요" },
              { title: "채팅으로 자세한 내용을 이야기할 수 있어요" },
            ]}
          />
        </section>
      </div>
    </SubScreen>
  );
}

export default OwnerAssignPage;
