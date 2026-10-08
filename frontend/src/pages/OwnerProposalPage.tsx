import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  Button,
  CategoryBadge,
  FlowBar,
  InfoRows,
  LoadNotice,
  ReferencePhotos,
  SubScreen,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  ProposalRejectDialogs,
  StudentBox,
  proposalStudentRecord,
  receivedOnText,
  receivedProposalFlowSteps,
  receivedProposalInProgress,
  receivedProposalStatusLabel,
  studentMetaText,
  useProposalReject,
} from "../features/owner";
import {
  estimatedDeadlineText,
  expectedDaysText,
  proposalBadgeNames,
  useProposalDetail,
} from "../features/proposal";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerDetailPage.css";
import "./OwnerProposalPage.css";

/**
 * 피그마 「받은 제안 상세」. GET /proposals/{id} (ADR 0025).
 * 결제한 제안(AWAITING_START · ACCEPTED)은 확정된 작업 조건(agreement)을 보인다.
 * 결정 대기(PENDING)일 때만 아래에 「거절하기」 · 「수락하기」. 「거절하기」는 확인 팝업 뒤
 * POST /proposals/{id}/reject 로 거절하고 「학생의 제안을 거절했어요」 → 이전 화면 (ADR 0033).
 */
function OwnerProposalPage() {
  const { proposalId } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.activity("proposals"));
  const { load, reload } = useProposalDetail(proposalId);
  const reject = useProposalReject(reload);

  if (load.status === "notFound") return <OwnerMissing title="받은 제안" onBack={back} />;

  const proposal = load.status === "loaded" ? load.proposal : undefined;
  const agreement = proposal?.agreement ?? undefined;
  const jobStatus = agreement?.jobStatus;
  const steps = proposal && receivedProposalFlowSteps(proposal.status, jobStatus);
  const pending = proposal?.status === "PENDING";
  const estimated = proposal && pending ? estimatedDeadlineText(proposal) : undefined;
  const showAgreement =
    agreement && (proposal?.status === "AWAITING_START" || proposal?.status === "ACCEPTED");
  const photos = proposal?.referenceImageUrls ?? [];
  const receivedOn = proposal && receivedOnText(proposal.createdAt, proposal.rejectedAt);
  const student = proposal?.student;
  const studentMeta = student && studentMetaText(student.studentNumber, student.major);

  return (
    <SubScreen
      title="받은 제안"
      onBack={back}
      footer={
        proposal && pending ? (
          <div className="owner-detail__actions">
            <Button variant="secondary" onClick={() => reject.ask(proposal.proposalId)}>
              거절하기
            </Button>
            <Button onClick={() => navigate(OWNER_PATHS.proposalAccept(String(proposal.proposalId)))}>
              수락하기
            </Button>
          </div>
        ) : (
          <Button fullWidth onClick={back}>
            확인
          </Button>
        )
      }
    >
      {load.status !== "loaded" && (
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="제안을 불러오는 중이에요"
          errorText="제안을 불러오지 못했어요"
          onRetry={reload}
        />
      )}

      {proposal && student && (
        <div className="owner-detail owner-proposal">
          <div className="owner-detail__heading">
            <div className="owner-detail__title-row">
              <WorkKindIcon kind="proposal" size={28} />
              <h2 className="owner-detail__title">{proposal.title}</h2>
            </div>
            <div className="owner-detail__meta owner-proposal__meta">
              <span
                className={`owner-proposal__chip${
                  receivedProposalInProgress(proposal.status, jobStatus) ? " owner-proposal__chip--working" : ""
                }`}
              >
                {receivedProposalStatusLabel(proposal.status, jobStatus)}
              </span>
              {proposalBadgeNames(proposal.specialtyCategories).map((name) => (
                <CategoryBadge key={name} field={name} />
              ))}
              {receivedOn && <span>{receivedOn}</span>}
            </div>
          </div>

          {steps && <FlowBar steps={steps} />}

          <div className="owner-proposal__empathy">
            <AppImage name="iconHeart" width={24} alt="" />
            <div>
              <strong className="owner-proposal__empathy-title">
                학생 손님 {proposal.likeCount}명이 공감했어요
              </strong>
              <p className="owner-proposal__empathy-sub">
                가게를 이용하는 학생들도 필요하다고 느낀 제안이에요
              </p>
            </div>
          </div>

          <StudentBox
            name={student.name}
            lines={[studentMeta, proposalStudentRecord(student)]}
            onProfile={() => navigate(OWNER_PATHS.student(String(student.studentProfileId)))}
          />

          {showAgreement && agreement && (
            <section className="owner-detail__section">
              <h2 className="owner-detail__section-title">정한 작업 조건</h2>
              <div className="owner-detail__box">
                <InfoRows
                  rows={[
                    { label: "작업비", value: formatWon(agreement.budget) },
                    { label: "초안 마감", value: formatMonthDay(agreement.draftDeadline) },
                    { label: "최종 마감", value: formatMonthDay(agreement.finalDeadline) },
                    { label: "수정 횟수", value: `${agreement.revisionCount}회` },
                  ]}
                />
              </div>
              {agreement.messageToStudent?.trim() && (
                <>
                  <h3 className="owner-proposal__message-title">학생에게 한마디</h3>
                  <p className="owner-detail__text">{agreement.messageToStudent}</p>
                </>
              )}
            </section>
          )}

          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">손님 눈으로 본 문제</h2>
            <p className="owner-detail__text">{proposal.customerProblem}</p>
          </section>

          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">이렇게 바꿔 드릴게요</h2>
            <p className="owner-detail__text">{proposal.proposedSolution}</p>
          </section>

          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">작업계획서</h2>
            <WorkPlan plan={proposal.workPlan} />
          </section>

          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">희망 작업비 · 예상 기간</h2>
            <div className="owner-detail__box">
              <InfoRows
                size="large"
                rows={[
                  { label: "희망 작업비", value: formatWon(proposal.proposedFee) },
                  { label: "예상 기간", value: expectedDaysText(proposal.draftDays, proposal.finalDays) },
                ]}
              />
            </div>
            {estimated && <p className="owner-detail__footnote">{estimated}</p>}
          </section>

          {photos.length > 0 && (
            <section className="owner-detail__section">
              <h2 className="owner-detail__section-title">참고 사진</h2>
              <ReferencePhotos urls={photos} />
            </section>
          )}

          {pending && (
            <p className="owner-detail__footnote">
              「수락하기」를 누르면 이 제안을 바탕으로 의뢰서를 만들어요. 희망 작업비를 참고해 작업비와
              수정 횟수를 그때 정하고, 마감일은 결제한 날부터 학생이 제안한 기간으로 정해져요.
            </p>
          )}
        </div>
      )}

      <ProposalRejectDialogs reject={reject} onDone={back} />
    </SubScreen>
  );
}

export default OwnerProposalPage;
