import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  LabelChip,
  LoadNotice,
  ReferencePhotos,
  RoleAvatar,
  SubScreen,
  WorkKindIcon,
} from "../components";
import { categoryNames, jobTaskNames, useJobDetail } from "../features/explore";
import { OWNER_PATHS, OwnerMissing, similarRequestState } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { withSubject } from "../lib/korean";
import { FIELDS } from "../types/field";
import "./OwnerDetailPage.css";
import "./OwnerExploreDetailPage.css";

/**
 * 피그마 「의뢰서 보기 (다른 가게 · 읽기 전용)」. GET /jobs/{id} (ADR 0026).
 * 작업비 · 마감일 같은 조건은 숨기고 의뢰 내용만 보여 준다. 같은 분야 · 같은 일로 우리 가게 의뢰를 시작할 수 있다.
 */
function OwnerExploreRequestPage() {
  const { requestId } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.explore);
  const { load, reload } = useJobDetail(requestId);

  if (load.status === "notFound") return <OwnerMissing title="의뢰서" onBack={back} />;

  const job = load.status === "loaded" ? load.job : undefined;
  const badges = job ? categoryNames(job.specialtyCategories) : [];
  const field = FIELDS.find((f) => badges.includes(f));
  const tasks = job ? jobTaskNames(job) : [];
  const photos = job?.referenceImageUrls ?? [];

  return (
    <SubScreen
      title="의뢰서"
      onBack={back}
      footer={
        job && (
          <Button
            fullWidth
            onClick={() =>
              navigate(
                OWNER_PATHS.newRequest,
                field ? { state: similarRequestState(field, tasks) } : undefined,
              )
            }
          >
            우리 가게에도 비슷한 의뢰 만들기
          </Button>
        )
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
          <p className="owner-explore-detail__notice">
            <b aria-hidden="true">ⓘ</b>
            {withSubject(job.storeName ?? "다른 가게")} 올린 의뢰예요. 지원은 학생만 할 수 있고, 사장님은
            참고만 할 수 있어요.
          </p>

          <div className="owner-detail__heading">
            <div className="owner-detail__title-row">
              <WorkKindIcon kind="request" size={28} />
              <h2 className="owner-detail__title">{job.title}</h2>
            </div>
            <div className="owner-detail__meta">
              {badges.map((name) => (
                <CategoryBadge key={name} field={name} />
              ))}
            </div>
          </div>

          {job.storeName && (
            <div className="owner-explore-detail__store">
              <RoleAvatar role="owner" />
              <strong>{job.storeName}</strong>
            </div>
          )}

          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">할 일</h2>
            <div className="owner-explore-detail__chips">
              {tasks.map((task) => (
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
        </div>
      )}
    </SubScreen>
  );
}

export default OwnerExploreRequestPage;
