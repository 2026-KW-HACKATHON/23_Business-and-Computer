import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  FlowBar,
  InfoRows,
  LabelChip,
  LoadNotice,
  ReferencePhotos,
  SubScreen,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import { chatListStatusText, chatPlanOf, chatWorkFlowIndex, chatWorkStageOf, useChatRooms } from "../features/chat";
import { categoryNames, jobStatusLabel, jobTaskNames, useJobDetail } from "../features/explore";
import { STUDENT_PATHS, StoreBox, StudentMissing, flowSteps } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";

type LoadedJob = Extract<ReturnType<typeof useJobDetail>["load"], { status: "loaded" }>["job"];

/**
 * 피그마 「의뢰서 전체 보기」(학생). GET /jobs/{id} (ADR 0026).
 * 조건 · 할 일 · 맡기고 싶은 일과 선택된 뒤의 진행 순서. 모집 중(OPEN)이면 아래에서 지원한다.
 * 가게 이름(storeName) · 주소(storeAddress) · 지원 여부(applied)는 서버가 줄 때만 보인다.
 * 사장님이 올린 참고 사진(referenceImageUrls)이 있으면 「참고 자료」에 보인다.
 * 내가 뽑혀 맡은 의뢰(applied ACCEPTED)면 「의뢰서 전체 보기 · 작업 중」: 지금 단계 흐름 막대와
 * 의뢰 조건 아래 내 작업계획서 (ADR 0045)
 */
function StudentRequestFullPage() {
  const { requestId } = useParams();
  const back = useBack(STUDENT_PATHS.explore);
  const { load, reload } = useJobDetail(requestId);

  if (load.status === "notFound") return <StudentMissing title="의뢰서" onBack={back} />;
  if (load.status !== "loaded") {
    return (
      <SubScreen title="의뢰서" onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="의뢰서를 불러오는 중이에요"
          errorText="의뢰서를 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  return load.job.applied === "ACCEPTED" ? (
    <MyRequestView job={load.job} onBack={back} />
  ) : (
    <OpenRequestView job={load.job} onBack={back} />
  );
}

/** 모집 중이거나 남의 의뢰: 진행 순서 안내, 모집 중이면 지원 */
function OpenRequestView({ job, onBack }: { job: LoadedJob; onBack: () => void }) {
  const navigate = useNavigate();
  const recruiting = job.status === "OPEN";
  const steps = [
    {
      title: "사장님이 작업비를 안전결제로 맡겨요",
      sub: "골목인턴이 보관하다가 완료되면 보내 드려요",
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
      onBack={onBack}
      footer={
        recruiting &&
        (job.applied != null ? (
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
      <div className="student-detail">
        <RequestHeading
          job={job}
          meta={!recruiting && job.applied === "REJECTED" ? "다른 학생이 선택됐어요" : jobStatusLabel(job.status)}
        />
        <FlowBar tone="student" steps={flowSteps("의뢰", 0, recruiting ? "모집 중" : "모집 끝")} />
        {job.storeName && <StoreBox name={job.storeName} address={job.storeAddress ?? undefined} />}
        <RequestTerms job={job} />
        <RequestContent job={job} />

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

/**
 * 내가 맡은 의뢰. 지금 단계는 채팅방(GET /me/chat-rooms)으로, 내 작업계획서는 채팅방의
 * 지원서로 보인다. 채팅방을 못 찾으면 의뢰 상태만 보이고 작업계획서 칸은 숨긴다
 */
function MyRequestView({ job, onBack }: { job: LoadedJob; onBack: () => void }) {
  const { load: roomsLoad } = useChatRooms();
  const room = roomsLoad.status === "loaded" ? roomsLoad.rooms.find((r) => r.jobId === job.id) : undefined;
  const stage = room && chatWorkStageOf(room);
  const plan = room && chatPlanOf(room);
  const flowIndex = chatWorkFlowIndex(stage);
  const flowSub = stage === "draftArrived" || stage === "revisionArrived" ? "확인 중" : "작업 중";

  return (
    <SubScreen
      title="의뢰서"
      onBack={onBack}
      footer={
        <Button tone="student" fullWidth onClick={onBack}>
          확인
        </Button>
      }
    >
      <div className="student-detail">
        <RequestHeading job={job} meta={(room && chatListStatusText(room, "student")) || jobStatusLabel(job.status)} />
        {flowIndex !== undefined && (
          <FlowBar tone="student" steps={flowSteps("의뢰", flowIndex, flowIndex < 5 ? flowSub : undefined)} />
        )}
        {job.storeName && <StoreBox name={job.storeName} address={job.storeAddress ?? undefined} />}
        <RequestTerms job={job} />
        {plan && (
          <section className="student-detail__section">
            <h2 className="student-detail__section-title">내 작업계획서</h2>
            <WorkPlan plan={plan} />
          </section>
        )}
        <RequestContent job={job} />
      </div>
    </SubScreen>
  );
}

function RequestHeading({ job, meta }: { job: LoadedJob; meta: string }) {
  return (
    <div className="student-detail__heading">
      <div className="student-detail__title-row">
        <WorkKindIcon kind="request" size={28} />
        <h2 className="student-detail__title">{job.title}</h2>
      </div>
      <div className="student-detail__meta">
        {categoryNames(job.specialtyCategories).map((name) => (
          <CategoryBadge key={name} field={name} />
        ))}
        {meta}
      </div>
    </div>
  );
}

function RequestTerms({ job }: { job: LoadedJob }) {
  return (
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
  );
}

/** 할 일 · 맡기고 싶은 일 · 참고 자료 */
function RequestContent({ job }: { job: LoadedJob }) {
  const photos = job.referenceImageUrls ?? [];
  return (
    <>
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

      {photos.length > 0 && (
        <section className="student-detail__section">
          <h2 className="student-detail__section-title">참고 자료</h2>
          <ReferencePhotos urls={photos} />
        </section>
      )}
    </>
  );
}

export default StudentRequestFullPage;
