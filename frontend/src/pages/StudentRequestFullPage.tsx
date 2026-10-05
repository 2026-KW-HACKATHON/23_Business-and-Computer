import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  FlowBar,
  InfoRows,
  LabelChip,
  SubScreen,
  WorkKindIcon,
} from "../components";
import { categoryNames, jobStatusLabel, jobTaskNames, useJobDetail } from "../features/explore";
import { LoadNotice, STUDENT_PATHS, StoreBox, StudentMissing, flowSteps } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";

/**
 * 피그마 「의뢰서 전체 보기」(학생). GET /jobs/{id} (ADR 0026).
 * 조건 · 할 일 · 맡기고 싶은 일과 선택된 뒤의 진행 순서. 모집 중(OPEN)이면 아래에서 지원한다.
 * 가게 이름(storeName) · 주소(storeAddress) · 지원 여부(applied)는 서버가 줄 때만 보인다.
 */
function StudentRequestFullPage() {
  const { requestId } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.explore);
  const { load, reload } = useJobDetail(requestId);

  if (load.status === "notFound") return <StudentMissing title="의뢰서" onBack={back} />;

  const job = load.status === "loaded" ? load.job : undefined;
  const recruiting = job?.status === "OPEN";

  const steps = job && [
    {
      title: "사장님이 작업비를 안전결제로 맡겨요",
      sub: "가꿈이 보관하다가 완료되면 보내 드려요",
    },
    { title: `${formatMonthDay(job.draftDeadline)}까지 초안을 보내요` },
    {
      title: `수정 요청이 오면 ${formatMonthDay(job.finalDeadline)}까지 최종본을 보내요`,
      sub: `수정은 ${job.revisionCount}회까지예요`,
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
        job &&
        recruiting &&
        (job.applied === true ? (
          <Button tone="student" variant="secondary" fullWidth disabled>
            지원했어요
          </Button>
        ) : (
          <Button tone="student" fullWidth onClick={() => navigate(STUDENT_PATHS.apply(String(job.id)))}>
            지원하기
          </Button>
        ))
      }
    >
      {load.status !== "loaded" && (
        <LoadNotice
          status={load.status}
          loadingText="의뢰서를 불러오는 중이에요"
          errorText="의뢰서를 불러오지 못했어요"
          onRetry={reload}
        />
      )}

      {job && steps && (
        <div className="student-detail">
          <div className="student-detail__heading">
            <div className="student-detail__title-row">
              <WorkKindIcon kind="request" size={28} />
              <h2 className="student-detail__title">{job.title}</h2>
            </div>
            <div className="student-detail__meta">
              {categoryNames(job.specialtyCategories).map((name) => (
                <CategoryBadge key={name} field={name} />
              ))}
              {jobStatusLabel(job.status)}
            </div>
          </div>

          <FlowBar tone="student" steps={flowSteps("의뢰", 0, recruiting ? "모집 중" : "모집 끝")} />

          {job.storeName && <StoreBox name={job.storeName} address={job.storeAddress ?? undefined} />}

          <div className="student-detail__box">
            <InfoRows
              rows={[
                { label: "작업비", value: formatWon(job.budget) },
                { label: "초안 마감", value: formatMonthDayWeekday(job.draftDeadline) },
                { label: "최종 마감", value: formatMonthDayWeekday(job.finalDeadline) },
                { label: "수정", value: `${job.revisionCount}회` },
              ]}
            />
          </div>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">할 일</h2>
            <div className="student-detail__chips">
              {jobTaskNames(job).map((task) => (
                <LabelChip key={task} label={task} />
              ))}
            </div>
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">맡기고 싶은 일</h2>
            <p className="student-detail__text">{job.description}</p>
          </section>

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
      )}
    </SubScreen>
  );
}

export default StudentRequestFullPage;
