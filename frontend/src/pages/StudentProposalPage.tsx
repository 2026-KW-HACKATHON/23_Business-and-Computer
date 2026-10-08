import { useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  Button,
  CategoryBadge,
  Dialog,
  FlowBar,
  InfoRows,
  LoadNotice,
  ReferencePhotos,
  SubScreen,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import {
  STUDENT_PATHS,
  StoreBox,
  StudentMissing,
  sendProposalCancel,
  sentOnText,
  sentProposalFlowSteps,
  sentProposalInProgress,
  sentProposalStatusLabel,
  storeAddressText,
} from "../features/student";
import {
  estimatedDeadlineText,
  expectedDaysText,
  proposalBadgeNames,
  useProposalDetail,
} from "../features/proposal";
import { landingPath } from "../features/auth";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";

/**
 * 피그마 「보낸 제안서 상세 보기」. GET /proposals/{id} (ADR 0023).
 * 가게 주소(storeAddress) · 보낸 날짜(createdAt)가 없으면 그 줄만 숨긴다.
 * 수락된(AWAITING_START) · 작업 중(ACCEPTED) 제안은 확정된 작업 조건(agreement)을 보인다.
 * 수락됐으면 가게 칸 아래에 「사장님이 제안을 받아들였어요」. 의뢰서가 왔으면(AWAITING_START) 아래 버튼은
 * 「조건 확인하기」 → 작업 시작 (ADR 0029).
 * 수락 대기인 제안은 아래에 「제안 취소」 · 「확인」. 「제안 취소하기」는 POST /proposals/{id}/cancel 로 취소하고
 * 「제안을 취소했어요」 → 내 활동 (보낸 제안) (ADR 0033). 사장님이 결제하는 중이거나 이미 수락된 제안이면
 * 버튼 위에 안내를 띄운다.
 */
function StudentProposalPage() {
  const { proposalId } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.activity("proposals"));
  const { load, reload } = useProposalDetail(proposalId);
  const [cancelStep, setCancelStep] = useState<"closed" | "confirm" | "done">("closed");
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 보낸다
  const inFlight = useRef(false);

  if (load.status === "notFound") {
    return <StudentMissing title="보낸 제안" onBack={back} />;
  }

  const proposal = load.status === "loaded" ? load.proposal : undefined;
  const agreement = proposal?.agreement ?? undefined;
  const jobStatus = agreement?.jobStatus;
  const steps = proposal && sentProposalFlowSteps(proposal.status, jobStatus);
  const pending = proposal?.status === "PENDING";
  const estimated = proposal && pending ? estimatedDeadlineText(proposal) : undefined;
  const showAgreement =
    agreement && (proposal?.status === "AWAITING_START" || proposal?.status === "ACCEPTED");
  const photos = proposal?.referenceImageUrls ?? [];
  const sentOn = proposal && sentOnText(proposal.createdAt, proposal.rejectedAt);
  const accepted = proposal?.status === "AWAITING_START" || proposal?.status === "ACCEPTED";
  const cancellable = pending && jobStatus !== "CANCELLED";
  // 사장님이 결제해 의뢰서가 왔으면 조건을 확인하고 작업을 시작한다
  const startable = proposal?.status === "AWAITING_START" && jobStatus !== "CANCELLED";
  const storeNote = accepted ? "사장님이 제안을 받아들였어요" : undefined;

  const cancel = async () => {
    if (!proposal || inFlight.current) return;
    inFlight.current = true;
    setCancelling(true);
    setCancelError(null);
    const result = await sendProposalCancel(proposal.proposalId);
    inFlight.current = false;
    setCancelling(false);
    if (result.status === "cancelled") {
      setCancelStep("done");
      return;
    }
    setCancelStep("closed");
    switch (result.status) {
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "forbidden":
        window.alert("제안을 보낸 학생만 취소할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "paymentPending":
        setCancelError("사장님이 결제하는 중이라 지금은 취소할 수 없어요");
        break;
      case "notFound":
      case "notAvailable":
        setCancelError("이미 수락됐거나 끝난 제안이라 취소할 수 없어요");
        reload();
        break;
      default:
        setCancelError("잠시 후 다시 시도해 주세요");
    }
  };

  return (
    <SubScreen
      title="보낸 제안"
      onBack={back}
      footer={
        startable && proposal ? (
          <Button
            tone="student"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.proposalStart(String(proposal.proposalId)))}
          >
            조건 확인하기
          </Button>
        ) : cancellable ? (
          <>
            {cancelError && (
              <p className="student-detail__send-error" role="alert">
                {cancelError}
              </p>
            )}
            <div className="student-detail__actions">
              <Button variant="secondary" onClick={() => setCancelStep("confirm")}>
                제안 취소
              </Button>
              <Button tone="student" onClick={back}>
                확인
              </Button>
            </div>
          </>
        ) : (
          <Button tone="student" fullWidth onClick={back}>
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

      {proposal && (
        <div className="student-detail student-proposal">
          <div className="student-detail__heading">
            <div className="student-detail__title-row">
              <WorkKindIcon kind="proposal" size={28} />
              <h2 className="student-detail__title">{proposal.title}</h2>
            </div>
            <div className="student-detail__meta student-proposal__meta">
              <span
                className={`student-proposal__chip${
                  sentProposalInProgress(proposal.status, jobStatus) ? " student-proposal__chip--working" : ""
                }`}
              >
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
            <div>
              <strong className="student-proposal__empathy-title">
                학생 손님 {proposal.likeCount}명이 공감했어요
              </strong>
              <p className="student-proposal__empathy-sub">
                공감이 많이 모이면 사장님께 한 번 더 알려 드려요
              </p>
            </div>
          </div>

          <StoreBox
            name={proposal.storeName}
            address={storeAddressText(proposal.storeAddress)}
            note={storeNote}
          />

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
        description={"사장님께 보낸 제안이 사라지고,\n모인 공감도 함께 없어져요."}
        onClose={() => setCancelStep("closed")}
        actions={
          <>
            <Button
              loading={cancelling}
              loadingLabel="취소하는 중"
              tone="student"
              fullWidth
              disabled={cancelling}
              onClick={() => void cancel()}
            >
              제안 취소하기
            </Button>
            <Button
              variant="secondary"
              fullWidth
              disabled={cancelling}
              onClick={() => setCancelStep("closed")}
            >
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
