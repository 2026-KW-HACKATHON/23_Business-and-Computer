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
  TextButton,
  WorkKindIcon,
} from "../components";
import { categoryNames, jobStatusLabel, jobTaskNames, useJobDetail } from "../features/explore";
import {
  OWNER_PATHS,
  OwnerMissing,
  flowSteps,
  parsePositiveId,
  useJobApplications,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerDetailPage.css";
import "./OwnerRequestPage.css";

/**
 * 피그마 「보낸 의뢰>상세보기」. GET /jobs/{id} 의 의뢰서와 학생을 고른 뒤의 진행 안내 (ADR 0030).
 * 모집 중이면 지원자 수(GET /jobs/{id}/applications)와 「의뢰 취소」가 보인다.
 */
function OwnerRequestPage() {
  const { requestId } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.activity("sent"));
  const { load, reload } = useJobDetail(requestId);
  const job = load.status === "loaded" ? load.job : undefined;
  const open = job?.status === "OPEN";
  const { load: applicants } = useJobApplications(
    open ? parsePositiveId(requestId) : undefined,
    "LATEST",
  );

  if (load.status === "notFound") return <OwnerMissing title="보낸 의뢰" onBack={back} />;

  const applicantCount = applicants.status === "loaded" ? applicants.data.applicantCount : undefined;
  const meta = !job
    ? ""
    : !open
      ? jobStatusLabel(job.status)
      : applicantCount === undefined
        ? "모집 중"
        : applicantCount === 0
          ? "모집 중, 아직 지원자가 없어요"
          : `모집 중, 지원자 ${applicantCount}명`;
  const photos = job?.referenceImageUrls ?? [];
  const steps = job
    ? [
        {
          title: `작업비 ${formatWon(job.budget)}을 안전결제로 맡겨요`,
          sub: "골목인턴이 보관하다가 완료되면 학생에게 보내요",
        },
        { title: `${formatMonthDay(job.draftDeadline)}까지 초안이 도착해요` },
        {
          title: `수정을 요청하면 ${formatMonthDay(job.finalDeadline)}까지 최종본이 와요`,
          sub: `수정은 ${job.revisionCount}회까지예요`,
        },
        {
          title: "완료를 확인하면 학생에게 정산돼요",
          sub: "결과물을 받고 7일 동안 답이 없으면 자동 완료",
        },
      ]
    : [];

  return (
    <SubScreen
      title="보낸 의뢰"
      onBack={back}
      footer={
        <Button fullWidth onClick={back}>
          확인
        </Button>
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

      {job && (
        <div className="owner-detail">
          <div className="owner-detail__heading">
            <div className="owner-detail__title-row">
              <WorkKindIcon kind="request" size={28} />
              <h2 className="owner-detail__title">{job.title}</h2>
            </div>
            <div className="owner-detail__meta">
              {categoryNames(job.specialtyCategories).map((name) => (
                <CategoryBadge key={name} field={name} />
              ))}
              {meta}
            </div>
          </div>

          {open && <FlowBar steps={flowSteps("의뢰", 0, "모집 중")} />}

          <div className="owner-request__terms">
            <InfoRows
              rows={[
                { label: "작업비", value: formatWon(job.budget) },
                { label: "초안 마감", value: formatMonthDayWeekday(job.draftDeadline) },
                { label: "최종 마감", value: formatMonthDayWeekday(job.finalDeadline) },
                { label: "수정", value: `${job.revisionCount}회` },
              ]}
            />
            {open && (
              <TextButton
                className="owner-request__cancel"
                onClick={() => navigate(OWNER_PATHS.requestCancel(String(job.id)))}
              >
                의뢰 취소
              </TextButton>
            )}
          </div>

          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">할 일</h2>
            <div className="owner-request__tasks">
              {jobTaskNames(job).map((task) => (
                <LabelChip key={task} label={task} />
              ))}
            </div>
          </section>

          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">맡기고 싶은 일</h2>
            <p className="owner-detail__text">{job.description}</p>
          </section>

          {photos.length > 0 && (
            <section className="owner-detail__section">
              <h2 className="owner-detail__section-title">참고 사진</h2>
              <ReferencePhotos urls={photos} />
            </section>
          )}

          <div className="owner-request__guide">
            <h2 className="owner-request__guide-title">학생을 고르면 이렇게 진행돼요</h2>
            <ol className="owner-request__guide-list">
              {steps.map((step, i) => (
                <li key={step.title} className="owner-request__guide-step">
                  <span className="owner-request__guide-number">{i + 1}</span>
                  <span className="owner-request__guide-text">
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

export default OwnerRequestPage;
