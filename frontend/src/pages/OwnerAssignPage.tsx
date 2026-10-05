import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  Button,
  FlowBar,
  InfoRows,
  NumberedSteps,
  RoleAvatar,
  SubScreen,
  TrustChips,
  WorkPlan,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  checkoutWorkId,
  flowSteps,
  studentRecord,
  useOwnerRequest,
  useStudentProfile,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerAssignPage.css";

/** 피그마 「이 학생에게 맡기기」. 맡길 학생 · 작업계획서 · 의뢰 조건을 한 번 더 확인한다 */
function OwnerAssignPage() {
  const { requestId = "", studentId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.requestApplicants(requestId));
  const request = useOwnerRequest(requestId);
  const profile = useStudentProfile(studentId);
  const applicant = request?.applicants.find((a) => a.student.id === studentId);

  if (!request || !applicant) return <OwnerMissing title="이 학생에게 맡기기" onBack={back} />;
  const { student, plan } = applicant;

  return (
    <SubScreen
      title="이 학생에게 맡기기"
      onBack={back}
      footer={
        <div className="owner-assign__actions">
          <Button
            fullWidth
            onClick={() => navigate(OWNER_PATHS.workPay(checkoutWorkId(request.id, student.id)))}
          >
            네, 맡길게요
          </Button>
          <Button variant="secondary" fullWidth onClick={back}>
            좀 더 고민해볼래요
          </Button>
        </div>
      }
    >
      <div className="owner-assign">
        <FlowBar steps={flowSteps("의뢰", 1, "결제 후 시작")} />

        <div className="owner-assign__intro">
          <h2 className="owner-assign__title">{`${student.name} 학생에게\n이 의뢰를 맡길까요?`}</h2>
          <p className="owner-assign__description">맡기면 다른 지원자들에게는 마감 안내가 가요</p>
        </div>

        <section className="owner-assign__card">
          <div className="owner-assign__student">
            <RoleAvatar role="student" size={48} />
            <div className="owner-assign__student-info">
              <strong className="owner-assign__name">{student.name} 학생</strong>
              <span className="owner-assign__meta">
                {`${student.department} ${student.year}\n${studentRecord(student)}`}
              </span>
            </div>
          </div>
          {profile && (
            <TrustChips proposalCount={profile.proposalCount} noShowCount={profile.noShowCount} />
          )}
          <WorkPlan plan={plan} />
        </section>

        <section className="owner-assign__card">
          <div className="owner-assign__request-head">
            <AppImage name="iconCardRequest" />
            <h3 className="owner-assign__request-title">{request.title}</h3>
          </div>
          <InfoRows
            rows={[
              { label: "작업비", value: formatWon(request.budget) },
              { label: "초안 마감", value: formatMonthDayWeekday(request.draftDue) },
              { label: "최종 마감", value: formatMonthDayWeekday(request.finalDue) },
              { label: "수정", value: `${request.revisionLimit}회` },
            ]}
          />
        </section>

        <section className="owner-assign__process">
          <h3 className="owner-assign__process-title">맡기면 이렇게 진행돼요</h3>
          <NumberedSteps
            steps={[
              {
                title: `작업비 ${formatWon(request.budget)}을 안전결제로 맡겨요`,
                description: "가꿈이 보관하고, 완료를 확인하면 학생에게 보내요",
              },
              { title: "사장님과 학생이 책임 약관에 동의하면 작업이 시작돼요" },
              { title: "채팅으로 자세한 내용을 이야기할 수 있어요" },
            ]}
          />
        </section>
      </div>
    </SubScreen>
  );
}

export default OwnerAssignPage;
