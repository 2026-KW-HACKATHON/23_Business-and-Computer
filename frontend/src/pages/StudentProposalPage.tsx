import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  Button,
  CategoryBadge,
  Dialog,
  FlowBar,
  InfoRows,
  ReferencePhotos,
  SubScreen,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import {
  LoadNotice,
  STUDENT_PATHS,
  StoreBox,
  StudentMissing,
  estimatedDeadlineText,
  expectedDaysText,
  proposalBadgeNames,
  sentOnText,
  sentProposalFlowSteps,
  sentProposalStatusLabel,
  storeAddressText,
  useSentProposalDetail,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";

/**
 * 피그마 「보낸 제안서 상세 보기」. GET /proposals/{id} (ADR 0022).
 * 가게 주소(storeAddress) · 보낸 날짜(createdAt)가 없으면 그 줄만 숨긴다.
 * 수락된(AWAITING_START) · 작업 중(ACCEPTED) 제안은 확정된 작업 조건(agreement)을 보인다.
 * 아래 버튼은 「확인」 하나다. 「조건 확인하기」(작업 시작)는 다음 이슈, 「제안 취소」는 취소 API 가
 * 생기면 연결한다 (아래 취소 팝업은 그때 쓰려고 남겨 둔다).
 */
function StudentProposalPage() {
  const { proposalId } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.activity("proposals"));
  const { load, reload } = useSentProposalDetail(proposalId);
  const [cancelStep, setCancelStep] = useState<"closed" | "confirm" | "done">("closed");

  if (load.status === "notFound") {
    return <StudentMissing title="보낸 제안" onBack={back} />;
  }

  // 제안 취소 API 가 생기면 여기서 부르고 성공했을 때 완료 팝업을 연다
  const confirmCancel = () => setCancelStep("done");

  const proposal = load.status === "loaded" ? load.proposal : undefined;
  const agreement = proposal?.agreement ?? undefined;
  const jobStatus = agreement?.jobStatus;
  const steps = proposal && sentProposalFlowSteps(proposal.status, jobStatus);
  const pending = proposal?.status === "PENDING";
  const estimated = proposal && pending ? estimatedDeadlineText(proposal) : undefined;
  const showAgreement =
    agreement && (proposal?.status === "AWAITING_START" || proposal?.status === "ACCEPTED");
  const photos = proposal?.referenceImageUrls ?? [];
  const sentOn = proposal && sentOnText(proposal.createdAt);

  return (
    <SubScreen
      title="보낸 제안"
      onBack={back}
      footer={
        <Button tone="student" fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      {load.status !== "loaded" && (
        <LoadNotice
          status={load.status}
          loadingText="제안을 불러오는 중이에요"
          errorText="제안을 불러오지 못했어요"
          onRetry={reload}
        />
      )}

      {proposal && (
        <div className="student-detail student-proposal">
          <div className="student-detail__heading">
            <div className="student-detail__title-row">
              <WorkKindIcon kind="proposal" size={28} />
              <h2 className="student-detail__title">{proposal.title}</h2>
            </div>
            <div className="student-detail__meta student-proposal__meta">
              <span className="student-proposal__chip">
                {sentProposalStatusLabel(proposal.status, jobStatus)}
              </span>
              {proposalBadgeNames(proposal.specialtyCategories).map((name) => (
                <CategoryBadge key={name} field={name} />
              ))}
              {sentOn && <span>{sentOn}</span>}
            </div>
          </div>

          {steps && <FlowBar tone="student" steps={steps} />}

          <div className="student-proposal__empathy">
            <AppImage name="iconHeart" width={24} alt="" />
            <strong className="student-proposal__empathy-title">
              학생 손님 {proposal.likeCount}명이 공감했어요
            </strong>
          </div>

          <StoreBox name={proposal.storeName} address={storeAddressText(proposal.storeAddress)} />

          {showAgreement && agreement && (
            <section className="student-detail__section">
              <h2 className="student-detail__section-title">사장님이 정한 작업 조건</h2>
              <div className="student-detail__box">
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
                  <h3 className="student-proposal__message-title">사장님 메시지</h3>
                  <p className="student-detail__text">{agreement.messageToStudent}</p>
                </>
              )}
            </section>
          )}

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">손님 눈으로 본 문제</h2>
            <p className="student-detail__text">{proposal.customerProblem}</p>
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">이렇게 바꿔 드릴게요</h2>
            <p className="student-detail__text">{proposal.proposedSolution}</p>
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">작업계획서</h2>
            <WorkPlan plan={proposal.workPlan} />
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">희망 작업비 · 예상 기간</h2>
            <div className="student-detail__box">
              <InfoRows
                size="large"
                rows={[
                  { label: "희망 작업비", value: formatWon(proposal.proposedFee) },
                  { label: "예상 기간", value: expectedDaysText(proposal.draftDays, proposal.finalDays) },
                ]}
              />
            </div>
            {estimated && <p className="student-detail__footnote">{estimated}</p>}
          </section>

          {photos.length > 0 && (
            <section className="student-detail__section">
              <h2 className="student-detail__section-title">참고 사진</h2>
              <ReferencePhotos urls={photos} />
            </section>
          )}

          {pending && (
            <p className="student-detail__footnote">
              사장님이 수락하면 이 제안을 바탕으로 작업비·마감일·수정 횟수를 정한 의뢰서가 와요.
            </p>
          )}
        </div>
      )}

      <Dialog
        open={cancelStep === "confirm"}
        image="warningStudent"
        title="제안을 취소할까요?"
        description="사장님께 보낸 제안이 사라지고, 모인 공감도 함께 없어져요."
        onClose={() => setCancelStep("closed")}
        actions={
          <>
            <Button tone="student" fullWidth onClick={confirmCancel}>
              제안 취소하기
            </Button>
            <Button variant="secondary" fullWidth onClick={() => setCancelStep("closed")}>
              돌아가기
            </Button>
          </>
        }
      />
      <Dialog
        open={cancelStep === "done"}
        image="doneStudent"
        title="제안을 취소했어요"
        actions={
          <Button
            tone="student"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.activity("proposals"), { replace: true })}
          >
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default StudentProposalPage;
