import { Navigate, useParams } from "react-router-dom";
import {
  AppImage,
  Button,
  CategoryBadge,
  FlowBar,
  InfoRows,
  LoadNotice,
  ReferencePhotos,
  RoleAvatar,
  SubScreen,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import {
  STUDENT_PATHS,
  StoreBox,
  StudentMissing,
  peerRecord,
  sentOnText,
  sentProposalFlowSteps,
  sentProposalStatusLabel,
  storeAddressText,
  useSentProposals,
} from "../features/student";
import {
  expectedDaysText,
  proposalBadgeNames,
  useProposalDetail,
  useProposalLikes,
} from "../features/proposal";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";
import { studentTitle } from "../lib/korean";

/**
 * 피그마 「제안서 보기 (다른 학생 제안 · 공감)」. 다른 학생이 보낸 제안을 손님 입장에서 읽는다.
 * GET /proposals/{id} (ADR 0026). 「공감하기」로 공감하고, 공감한 뒤 「공감했어요」를 다시 누르면 취소한다.
 * 내 제안(GET /me/proposals 에 있음)이면 보낸 제안 상세로 바꾼다. 내 제안 목록을 불러오는 동안은
 * 불러오는 중으로 두고, 그 목록이 실패하면 다른 학생 제안으로 보인다.
 */
function StudentPeerProposalPage() {
  const { proposalId } = useParams();
  const back = useBack(STUDENT_PATHS.explore);
  const { load, reload } = useProposalDetail(proposalId);
  const { load: sent } = useSentProposals();
  const likes = useProposalLikes();

  const proposal = load.status === "loaded" ? load.proposal : undefined;
  const mine =
    sent.status === "loaded"
      ? sent.proposals.find(
          (p) => String(p.proposalId) === proposalId || p.proposalId === proposal?.proposalId,
        )
      : undefined;

  if (mine) return <Navigate to={STUDENT_PATHS.proposal(String(mine.proposalId))} replace />;
  if (load.status === "notFound") return <StudentMissing title="제안서" onBack={back} />;

  const shown = sent.status === "loading" ? undefined : proposal;
  const notice = load.status === "error" ? "error" : shown ? undefined : "loading";
  const steps = shown && sentProposalFlowSteps(shown.status);
  const sentOn = shown && sentOnText(shown.createdAt);
  const student = shown?.student ?? undefined;
  const photos = shown?.referenceImageUrls ?? [];
  const like =
    shown &&
    likes.likeOf(shown.proposalId, {
      likeCount: shown.likeCount,
      likedByMe: shown.likedByMe === true,
    });

  return (
    <SubScreen
      title="제안서"
      onBack={back}
      footer={
        shown &&
        like && (
          <Button
            tone="student"
            variant={like.likedByMe ? "secondary" : "primary"}
            fullWidth
            aria-pressed={like.likedByMe}
            onClick={() => likes.toggle(shown.proposalId, like)}
          >
            {like.likedByMe ? "공감했어요" : "공감하기"}
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

      {shown && (
        <div className="student-detail student-proposal">
          <p className="student-detail__notice">
            <b aria-hidden="true">ⓘ</b>
            다른 학생의 제안이에요. 손님으로서 공감되면 눌러 주세요.
          </p>

          <div className="student-detail__heading">
            <div className="student-detail__title-row">
              <WorkKindIcon kind="proposal" size={28} />
              <h2 className="student-detail__title">{shown.title}</h2>
            </div>
            <div className="student-detail__meta student-proposal__meta">
              <span className="student-proposal__chip">{sentProposalStatusLabel(shown.status)}</span>
              {proposalBadgeNames(shown.specialtyCategories).map((name) => (
                <CategoryBadge key={name} field={name} />
              ))}
              {sentOn && <span>{sentOn}</span>}
            </div>
          </div>

          {steps && <FlowBar tone="student" steps={steps} />}

          <div className="student-proposal__empathy">
            <AppImage name={like?.likedByMe ? "iconHeart" : "iconHeartEmpty"} width={24} alt="" />
            <div>
              <strong className="student-proposal__empathy-title">
                학생 손님 {like?.likeCount ?? shown.likeCount}명이 공감했어요
              </strong>
              {(like?.likeCount ?? shown.likeCount) > 0 && (
                <p className="student-proposal__empathy-sub">
                  가게를 이용하는 학생들도 필요하다고 느낀 제안이에요
                </p>
              )}
            </div>
          </div>

          {student && (
            <div className="student-proposal__student">
              <RoleAvatar role="student" />
              <div className="student-proposal__student-info">
                <strong className="student-proposal__student-name">{studentTitle(student.name)}</strong>
                <span className="student-proposal__student-sub">
                  {[
                    [student.major, student.studentNumber && `${student.studentNumber}학번`]
                      .filter(Boolean)
                      .join(" "),
                    peerRecord({
                      rating: student.averageRating,
                      completedCount: student.completedJobCount,
                    }),
                  ]
                    .filter(Boolean)
                    .join("\n")}
                </span>
              </div>
            </div>
          )}

          <StoreBox name={shown.storeName} address={storeAddressText(shown.storeAddress)} />

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">손님 눈으로 본 문제</h2>
            <p className="student-detail__text">{shown.customerProblem}</p>
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">이렇게 바꿔 드릴게요</h2>
            <p className="student-detail__text">{shown.proposedSolution}</p>
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">작업계획서</h2>
            <WorkPlan plan={shown.workPlan} />
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">희망 작업비 · 예상 기간</h2>
            <div className="student-detail__box">
              <InfoRows
                size="large"
                rows={[
                  { label: "희망 작업비", value: formatWon(shown.proposedFee) },
                  { label: "예상 기간", value: expectedDaysText(shown.draftDays, shown.finalDays) },
                ]}
              />
            </div>
          </section>

          {photos.length > 0 && (
            <section className="student-detail__section">
              <h2 className="student-detail__section-title">참고 사진</h2>
              <ReferencePhotos urls={photos} />
            </section>
          )}

          {likes.failedId === shown.proposalId ? (
            <p className="student-proposal__like-note student-proposal__like-note--error" role="alert">
              공감하지 못했어요. 잠시 후 다시 눌러 주세요.
            </p>
          ) : (
            <p className="student-proposal__like-note">
              공감은 다시 누르면 취소돼요. 내 제안에는 누를 수 없어요.
            </p>
          )}
        </div>
      )}
    </SubScreen>
  );
}

export default StudentPeerProposalPage;
