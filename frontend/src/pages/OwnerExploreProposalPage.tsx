import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  Button,
  CategoryBadge,
  LoadNotice,
  ReferencePhotos,
  RoleAvatar,
  SubScreen,
  WorkKindIcon,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  proposalStudentRecord,
  receivedProposalStatusLabel,
  similarRequestState,
  studentMetaText,
  useReceivedProposals,
} from "../features/owner";
import { proposalBadgeNames, proposalMonthDay, useProposalDetail } from "../features/proposal";
import { useBack } from "../hooks/useBack";
import { studentTitle, withSubject } from "../lib/korean";
import { FIELDS } from "../types/field";
import "./OwnerDetailPage.css";
import "./OwnerProposalPage.css";
import "./OwnerExploreDetailPage.css";

/**
 * 피그마 「제안서 보기 (다른 가게 · 읽기 전용)」. GET /proposals/{id} (ADR 0026).
 * 다른 가게가 받은 제안의 내용만 보여 주고 (희망 작업비 · 예상 기간은 숨김), 우리 가게 의뢰로 이어 갈 수 있다.
 * 우리 가게가 받은 제안(GET /me/received-proposals 에 있음)이면 받은 제안 상세로 바꾼다. 그 목록을
 * 불러오는 동안은 불러오는 중으로 두고, 목록이 실패하면 다른 가게 제안으로 보인다.
 */
function OwnerExploreProposalPage() {
  const { proposalId } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.explore);
  const { load, reload } = useProposalDetail(proposalId);
  const { load: received } = useReceivedProposals();

  const mine =
    received.status === "loaded"
      ? received.proposals.find((p) => String(p.proposalId) === proposalId)
      : undefined;
  if (mine) return <Navigate to={OWNER_PATHS.proposal(String(mine.proposalId))} replace />;
  if (load.status === "notFound") return <OwnerMissing title="제안서" onBack={back} />;

  const proposal =
    load.status === "loaded" && received.status !== "loading" ? load.proposal : undefined;
  const notice = load.status === "error" ? "error" : proposal ? undefined : "loading";
  const student = proposal?.student;
  const badges = proposal ? proposalBadgeNames(proposal.specialtyCategories) : [];
  const field = FIELDS.find((f) => badges.includes(f));
  const receivedOn = proposal && proposalMonthDay(proposal.createdAt);
  const photos = proposal?.referenceImageUrls ?? [];

  return (
    <SubScreen
      title="제안서"
      onBack={back}
      footer={
        proposal && (
          <Button
            fullWidth
            onClick={() =>
              navigate(OWNER_PATHS.newRequest, field ? { state: similarRequestState(field) } : undefined)
            }
          >
            우리 가게에도 비슷한 의뢰 만들기
          </Button>
        )
      }
    >
      {notice && (
        <LoadNotice
          status={notice}
          loadingText="제안을 불러오는 중이에요"
          errorText="제안을 불러오지 못했어요"
          onRetry={reload}
        />
      )}

      {proposal && (
        <div className="owner-detail owner-proposal">
          <p className="owner-explore-detail__notice">
            <b aria-hidden="true">ⓘ</b>
            {withSubject(proposal.storeName)} 받은 제안이에요. 읽기만 할 수 있어요.
          </p>

          <div className="owner-detail__heading">
            <div className="owner-detail__title-row">
              <WorkKindIcon kind="proposal" size={28} />
              <h2 className="owner-detail__title">{proposal.title}</h2>
            </div>
            <div className="owner-detail__meta">
              {badges.map((name) => (
                <CategoryBadge key={name} field={name} />
              ))}
              {[proposal.storeName, receivedOn, receivedProposalStatusLabel(proposal.status)]
                .filter(Boolean)
                .join(" · ")}
            </div>
          </div>

          <div className="owner-proposal__empathy">
            <AppImage name="iconHeart" width={24} alt="" />
            <div>
              <strong className="owner-proposal__empathy-title">
                학생 손님 {proposal.likeCount}명이 공감했어요
              </strong>
              {proposal.likeCount > 0 && (
                <p className="owner-proposal__empathy-sub">
                  가게를 이용하는 학생들도 필요하다고 느낀 제안이에요
                </p>
              )}
            </div>
          </div>

          {student && (
            <div className="owner-proposal__student">
              <RoleAvatar role="student" />
              <div className="owner-proposal__student-info">
                <strong className="owner-proposal__student-name">{studentTitle(student.name)}</strong>
                <span className="owner-proposal__student-sub">
                  {[studentMetaText(student.studentNumber, student.major), proposalStudentRecord(student)]
                    .filter(Boolean)
                    .join("\n")}
                </span>
              </div>
            </div>
          )}

          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">손님 눈으로 본 문제</h2>
            <p className="owner-detail__text">{proposal.customerProblem}</p>
          </section>

          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">이렇게 바꿔 드릴게요</h2>
            <p className="owner-detail__text">{proposal.proposedSolution}</p>
          </section>

          {photos.length > 0 && (
            <section className="owner-detail__section">
              <h2 className="owner-detail__section-title">참고 사진</h2>
              <ReferencePhotos urls={photos} />
            </section>
          )}

          <p className="owner-detail__footnote">
            다른 가게가 받은 제안이라 수락·문의는 그 가게 사장님만 할 수 있어요. 아이디어는 참고해
            보세요.
          </p>
        </div>
      )}
    </SubScreen>
  );
}

export default OwnerExploreProposalPage;
