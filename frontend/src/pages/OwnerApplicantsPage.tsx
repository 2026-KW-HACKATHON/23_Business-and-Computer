import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  FlowBar,
  InfoRows,
  LabelChip,
  RoleAvatar,
  SubScreen,
  TextButton,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  flowSteps,
  studentRecord,
  useOwnerRequest,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerDetailPage.css";
import "./OwnerApplicantsPage.css";

/** 피그마 「보낸 의뢰 상세 · 지원자 목록」. 작업계획서를 보고 맡길 학생을 고른다 */
function OwnerApplicantsPage() {
  const { requestId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const request = useOwnerRequest(requestId);

  if (!request) return <OwnerMissing title="지원자 목록" onBack={back} />;

  return (
    <SubScreen title="지원자 목록" onBack={back}>
      <div className="owner-detail owner-detail--tight">
        <div className="owner-applicants__summary">
          <div className="owner-applicants__summary-head">
            <WorkKindIcon kind="request" />
            <h2 className="owner-applicants__summary-title">{request.title}</h2>
          </div>
          <CategoryBadge field={request.field} />
          <InfoRows
            rows={[
              { label: "예산", value: formatWon(request.budget) },
              { label: "초안 마감", value: formatMonthDayWeekday(request.draftDue) },
              { label: "최종 마감", value: formatMonthDayWeekday(request.finalDue) },
            ]}
          />
          <div className="owner-applicants__divider" />
          <TextButton onClick={() => navigate(OWNER_PATHS.request(request.id))}>
            의뢰서 전체 보기
          </TextButton>
        </div>

        <FlowBar steps={flowSteps("의뢰", 0, "학생 고르기")} />

        <div className="owner-applicants__head">
          <div className="owner-applicants__title-row">
            <h2 className="owner-applicants__title">
              지원자 <span>{request.applicants.length}</span>
            </h2>
            <TextButton showFilter showChevron={false}>
              추천순
            </TextButton>
          </div>
          <p className="owner-applicants__description">
            전공·작업계획서·후기를 보고 맡길 학생을 골라 주세요
          </p>
        </div>

        {request.applicants.length === 0 ? (
          <p className="owner-applicants__empty">아직 지원한 학생이 없어요</p>
        ) : (
          <ul className="owner-applicants__list">
            {request.applicants.map(({ student, badges, plan }) => (
              <li key={student.id} className="owner-applicants__card">
                <div className="owner-applicants__student">
                  <RoleAvatar role="student" />
                  <div className="owner-applicants__student-info">
                    <span className="owner-applicants__name-row">
                      <strong>{student.name} 학생</strong>
                      <span>{student.year}</span>
                    </span>
                    <span className="owner-applicants__department">{student.department}</span>
                  </div>
                  <span
                    className={`owner-applicants__record${
                      student.completedCount === 0 ? " owner-applicants__record--new" : ""
                    }`}
                  >
                    {studentRecord(student)}
                  </span>
                </div>
                <div className="owner-applicants__badges">
                  {badges.map((badge) => (
                    <LabelChip key={badge} label={badge} />
                  ))}
                </div>
                <WorkPlan plan={plan} collapsible />
                <div className="owner-applicants__actions">
                  <Button
                    variant="secondary"
                    onClick={() =>
                      navigate(OWNER_PATHS.student(student.id), { state: { requestId: request.id } })
                    }
                  >
                    프로필 보기
                  </Button>
                  <Button onClick={() => navigate(OWNER_PATHS.assign(request.id, student.id))}>
                    이 학생에게 맡기기
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </SubScreen>
  );
}

export default OwnerApplicantsPage;
