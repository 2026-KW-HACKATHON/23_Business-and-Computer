import { useNavigate, useParams } from "react-router-dom";
import {
  AttachmentTiles,
  Button,
  CategoryBadge,
  LabelChip,
  RoleAvatar,
  SubScreen,
  WorkKindIcon,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  similarRequestState,
  useExploreDetail,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { withSubject } from "../lib/korean";
import "./OwnerDetailPage.css";
import "./OwnerExploreDetailPage.css";

/**
 * 피그마 「의뢰서 보기 (다른 가게 · 읽기 전용)」. 작업비 · 마감일 같은 조건은 숨기고
 * 의뢰 내용만 보여 준다. 같은 분야 · 같은 일로 우리 가게 의뢰를 시작할 수 있다.
 */
function OwnerExploreRequestPage() {
  const { requestId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.explore);
  const detail = useExploreDetail(requestId);

  if (detail?.kind !== "request") return <OwnerMissing title="의뢰서" onBack={back} />;

  return (
    <SubScreen
      title="의뢰서"
      onBack={back}
      footer={
        <Button
          fullWidth
          onClick={() =>
            navigate(OWNER_PATHS.newRequest, {
              state: similarRequestState(detail.field, detail.tasks),
            })
          }
        >
          우리 가게에도 비슷한 의뢰 만들기
        </Button>
      }
    >
      <div className="owner-detail">
        <p className="owner-explore-detail__notice">
          <b aria-hidden="true">ⓘ</b>
          {withSubject(detail.storeName)} 올린 의뢰예요. 지원은 학생만 할 수 있고, 사장님은 참고만
          할 수 있어요.
        </p>

        <div className="owner-detail__heading">
          <div className="owner-detail__title-row">
            <WorkKindIcon kind="request" size={28} />
            <h2 className="owner-detail__title">{detail.title}</h2>
          </div>
          <div className="owner-detail__meta">
            <CategoryBadge field={detail.field} />
          </div>
        </div>

        <div className="owner-explore-detail__store">
          <RoleAvatar role="owner" />
          <strong>{detail.storeName}</strong>
        </div>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">할 일</h2>
          <div className="owner-explore-detail__chips">
            {detail.tasks.map((task) => (
              <LabelChip key={task} label={task} />
            ))}
          </div>
        </section>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">맡기고 싶은 일</h2>
          <p className="owner-detail__text">{detail.description}</p>
        </section>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">참고 자료</h2>
          <AttachmentTiles names={detail.attachments} height={110} />
        </section>
      </div>
    </SubScreen>
  );
}

export default OwnerExploreRequestPage;
