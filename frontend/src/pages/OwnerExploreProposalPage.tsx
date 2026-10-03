import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  AttachmentTiles,
  Button,
  CategoryBadge,
  RoleAvatar,
  SubScreen,
  TextButton,
  WorkKindIcon,
} from "../components";
import {
  EXPLORE_PROGRESS_LABEL,
  OWNER_PATHS,
  OwnerMissing,
  similarRequestState,
  studentRecord,
  useExploreDetail,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { withSubject } from "../lib/korean";
import "./OwnerDetailPage.css";
import "./OwnerProposalPage.css";
import "./OwnerExploreDetailPage.css";

/**
 * 피그마 「제안서 보기 (다른 가게 · 읽기 전용)」. 다른 가게가 받은 제안의 내용만 보여 주고
 * (희망 작업비 · 예상 기간은 숨김), 우리 가게 의뢰로 이어 갈 수 있다.
 */
function OwnerExploreProposalPage() {
  const { proposalId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.explore);
  const detail = useExploreDetail(proposalId);

  if (detail?.kind !== "proposal") return <OwnerMissing title="제안서" onBack={back} />;
  const { student } = detail;

  return (
    <SubScreen
      title="제안서"
      onBack={back}
      footer={
        <Button
          fullWidth
          onClick={() =>
            navigate(OWNER_PATHS.newRequest, { state: similarRequestState(detail.field) })
          }
        >
          우리 가게에도 비슷한 의뢰 만들기
        </Button>
      }
    >
      <div className="owner-detail owner-proposal">
        <p className="owner-explore-detail__notice">
          <b aria-hidden="true">ⓘ</b>
          {withSubject(detail.storeName)} 받은 제안이에요. 읽기만 할 수 있어요.
        </p>

        <div className="owner-detail__heading">
          <div className="owner-detail__title-row">
            <WorkKindIcon kind="proposal" size={28} />
            <h2 className="owner-detail__title">{detail.title}</h2>
          </div>
          <div className="owner-detail__meta">
            <CategoryBadge field={detail.field} />
            {detail.storeName} · {formatMonthDay(detail.receivedOn)} ·{" "}
            {EXPLORE_PROGRESS_LABEL[detail.progress]}
          </div>
        </div>

        <div className="owner-proposal__empathy">
          <AppImage name="iconHeart" width={24} alt="" />
          <div>
            <strong className="owner-proposal__empathy-title">
              학생 손님 {detail.empathyCount}명이 공감했어요
            </strong>
            <p className="owner-proposal__empathy-sub">
              가게를 이용하는 학생들도 필요하다고 느낀 제안이에요
            </p>
          </div>
        </div>

        <div className="owner-proposal__student">
          <RoleAvatar role="student" />
          <div className="owner-proposal__student-info">
            <strong className="owner-proposal__student-name">{student.name} 학생</strong>
            <span className="owner-proposal__student-sub">
              {`${student.department} ${student.year}\n${studentRecord(student)}`}
            </span>
          </div>
          <TextButton onClick={() => navigate(OWNER_PATHS.student(student.id))}>프로필 보기</TextButton>
        </div>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">손님 눈으로 본 문제</h2>
          <p className="owner-detail__text">{detail.problem}</p>
        </section>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">이렇게 바꿔 드릴게요</h2>
          <p className="owner-detail__text">{detail.solution}</p>
        </section>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">참고 사진</h2>
          <AttachmentTiles names={detail.attachments} />
        </section>

        <p className="owner-detail__footnote">
          다른 가게가 받은 제안이라 수락·문의는 그 가게 사장님만 할 수 있어요. 아이디어는 참고해
          보세요.
        </p>
      </div>
    </SubScreen>
  );
}

export default OwnerExploreProposalPage;
