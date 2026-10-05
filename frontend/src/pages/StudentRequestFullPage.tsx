import { useNavigate, useParams } from "react-router-dom";
import {
  AttachmentTiles,
  Button,
  CategoryBadge,
  FlowBar,
  InfoRows,
  LabelChip,
  SubScreen,
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
import { formatMonthDay, formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";

/**
 * 피그마 「의뢰서 전체 보기」(학생). 조건 · 할 일 · 맡기고 싶은 일 · 참고 자료와
 * 선택된 뒤의 진행 순서. 모집 중이면 아래에서 지원한다.
 */
function StudentRequestFullPage() {
  const { requestId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.explore);
  const request = useStudentRequest(requestId);
  const store = useStore(request?.store.id);
  const application = useStudentApplication(requestId);

  if (!request) return <StudentMissing title="의뢰서" onBack={back} />;
  const recruiting = request.progress === "recruiting";
  const statusText = recruiting
    ? "모집 중"
    : request.progress === "closed"
      ? "다른 학생이 선택됐어요"
      : "끝난 의뢰예요";

  const steps = [
    {
      title: "사장님이 작업비를 안전결제로 맡겨요",
      sub: "가꿈이 보관하다가 완료되면 보내 드려요",
    },
    { title: `${formatMonthDay(request.draftDue)}까지 초안을 보내요` },
    {
      title: `수정 요청이 오면 ${formatMonthDay(request.finalDue)}까지 최종본을 보내요`,
      sub: `수정은 ${request.revisionLimit}회까지예요`,
    },
    {
      title: "완료 확인 후 작업비가 정산돼요",
      sub: "결과물을 보내고 7일 동안 답이 없으면 자동 완료",
    },
  ];

  return (
    <SubScreen
      title="의뢰서"
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
      <div className="student-detail">
        <div className="student-detail__heading">
          <div className="student-detail__title-row">
            <WorkKindIcon kind="request" size={28} />
            <h2 className="student-detail__title">{request.title}</h2>
          </div>
          <div className="student-detail__meta">
            <CategoryBadge field={request.field} />
            {statusText}
          </div>
        </div>

        <FlowBar
          tone="student"
          steps={flowSteps("의뢰", 0, recruiting ? "모집 중" : "모집 끝")}
        />

        <StoreBox name={request.store.name} address={store?.address} />

        <div className="student-detail__box">
          <InfoRows
            rows={[
              { label: "작업비", value: formatWon(request.budget) },
              { label: "초안 마감", value: formatMonthDayWeekday(request.draftDue) },
              { label: "최종 마감", value: formatMonthDayWeekday(request.finalDue) },
              { label: "수정", value: `${request.revisionLimit}회` },
            ]}
          />
        </div>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">할 일</h2>
          <div className="student-detail__chips">
            {request.tasks.map((task) => (
              <LabelChip key={task} label={task} />
            ))}
          </div>
        </section>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">맡기고 싶은 일</h2>
          <p className="student-detail__text">{request.description}</p>
        </section>

        {request.attachments.length > 0 && (
          <section className="student-detail__section">
            <h2 className="student-detail__section-title">참고 자료</h2>
            <AttachmentTiles names={request.attachments} height={110} />
          </section>
        )}

        <div className="student-detail__guide">
          <h2 className="student-detail__guide-title">선택되면 이렇게 진행돼요</h2>
          <ol className="student-detail__guide-list">
            {steps.map((step, i) => (
              <li key={step.title} className="student-detail__guide-step">
                <span className="student-detail__guide-number">{i + 1}</span>
                <span className="student-detail__guide-text">
                  <span>{step.title}</span>
                  {step.sub && <small>{step.sub}</small>}
                </span>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </SubScreen>
  );
}

export default StudentRequestFullPage;
