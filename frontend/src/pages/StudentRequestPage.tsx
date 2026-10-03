import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  FlowBar,
  InfoRows,
  SubScreen,
  TextButton,
  WorkKindIcon,
} from "../components";
import {
  STUDENT_PATHS,
  StoreBox,
  StudentMissing,
  flowSteps,
  useStore,
  useStudentApplication,
  useStudentRequest,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";

/**
 * 피그마 「의뢰 상세 · 지원자 목록」(학생). 탐색 카드에서 들어오는 의뢰 요약.
 * 「의뢰서 전체 보기」로 자세한 내용을 보고, 모집 중이면 바로 지원한다.
 */
function StudentRequestPage() {
  const { requestId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.explore);
  const request = useStudentRequest(requestId);
  const store = useStore(request?.store.id);
  const application = useStudentApplication(requestId);

  if (!request) return <StudentMissing title="의뢰 상세" onBack={back} />;
  const recruiting = request.progress === "recruiting";

  return (
    <SubScreen
      title="의뢰 상세"
      onBack={back}
      footer={
        recruiting &&
        (application ? (
          <Button tone="student" variant="secondary" fullWidth disabled>
            지원했어요
          </Button>
        ) : (
          <Button tone="student" fullWidth onClick={() => navigate(STUDENT_PATHS.apply(request.id))}>
            작업계획서 쓰고 지원하기
          </Button>
        ))
      }
    >
      <div className="student-detail student-detail--tight">
        <div className="student-detail__summary">
          <div className="student-detail__summary-head">
            <WorkKindIcon kind="request" />
            <h2 className="student-detail__summary-title">{request.title}</h2>
          </div>
          <CategoryBadge field={request.field} />
          <InfoRows
            rows={[
              { label: "예산", value: formatWon(request.budget) },
              { label: "초안 마감", value: formatMonthDayWeekday(request.draftDue) },
              { label: "최종 마감", value: formatMonthDayWeekday(request.finalDue) },
            ]}
          />
          <div className="student-detail__divider" />
          <TextButton onClick={() => navigate(STUDENT_PATHS.requestFull(request.id))}>
            의뢰서 전체 보기
          </TextButton>
        </div>

        <StoreBox name={request.store.name} address={store?.address} />

        <FlowBar
          tone="student"
          steps={flowSteps("의뢰", 0, recruiting ? "모집 중" : "모집 끝")}
        />
      </div>
    </SubScreen>
  );
}

export default StudentRequestPage;
