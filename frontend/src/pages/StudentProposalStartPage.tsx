import { useEffect, useRef, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Dialog,
  FlowBar,
  InfoRows,
  LoadNotice,
  NoteBox,
  NumberedSteps,
  ReferencePhotos,
  SubScreen,
  TurnNotice,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import { landingPath } from "../features/auth";
import { expectedDaysText, useProposalDetail } from "../features/proposal";
import {
  STUDENT_PATHS,
  StoreBox,
  StudentMissing,
  flowSteps,
  sendWorkDecline,
  sendWorkStart,
  storeAddressText,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, formatMonthDayWeekday, todayIsoDate } from "../lib/date";
import { formatWon } from "../lib/money";
import { AgreementCheckbox } from "../features/signup";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

type StartError = "notAvailable" | "declineNotAvailable" | "retry";

const START_ERROR_TEXT: Record<StartError, string> = {
  notAvailable: "지금은 작업을 시작할 수 없어요. 보낸 제안서에서 상태를 확인해 주세요",
  declineNotAvailable: "지금은 의뢰서를 거절할 수 없어요. 보낸 제안서에서 상태를 확인해 주세요",
  retry: "잠시 후 다시 시도해 주세요",
};

/**
 * 피그마 「작업 시작 - 의뢰서 확인·약관 동의」. 내 제안이 수락돼 사장님이 결제한 의뢰서가 오면(AWAITING_START)
 * GET /proposals/{id} 의 확정 조건(agreement)을 확인하고 약관에 동의해 POST /jobs/{jobId}/start 로 시작한다
 * (ADR 0029). 「이 조건은 어려워요」 → 「거절하기」는 POST /jobs/{jobId}/decline 로 거절하고 내 활동 (보낸 제안)으로
 * 간다 (ADR 0033). 맡겨 둔 작업비는 사장님께 모두 돌아간다.
 * 의뢰서가 온 상태가 아니면(시작 전 · 시작 뒤 · 취소) 보낸 제안서 상세로 바꾼다.
 */
function StudentProposalStartPage() {
  const { proposalId } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const { load, reload } = useProposalDetail(proposalId);
  const [agreed, setAgreed] = useState(false);
  const [popup, setPopup] = useState<"none" | "decline" | "started">("none");
  const [starting, setStarting] = useState(false);
  const [declining, setDeclining] = useState(false);
  const [startError, setStartError] = useState<StartError | null>(null);
  const [startedDraft, setStartedDraft] = useState<string>();
  // 다시 그려지기 전에 두 번 눌러도 한 번만 보낸다
  const inFlight = useRef(false);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestId = useRef(0);

  useEffect(() => {
    const latest = requestId;
    return () => {
      latest.current += 1;
    };
  }, []);

  if (load.status === "notFound") return <StudentMissing title="작업 시작" onBack={back} />;

  const proposal = load.status === "loaded" ? load.proposal : undefined;
  const agreement = proposal?.agreement ?? undefined;
  const jobId = proposal?.jobId ?? undefined;
  const ready =
    proposal?.status === "AWAITING_START" &&
    agreement !== undefined &&
    agreement.jobStatus !== "CANCELLED" &&
    jobId !== undefined;
  if (proposal && !ready && popup !== "started") {
    return <Navigate to={STUDENT_PATHS.proposal(String(proposal.proposalId))} replace />;
  }

  const start = async () => {
    if (jobId === undefined || inFlight.current) return;
    inFlight.current = true;
    const id = ++requestId.current;
    setStarting(true);
    setStartError(null);
    const result = await sendWorkStart(jobId);
    inFlight.current = false;
    if (id !== requestId.current) return;
    setStarting(false);

    switch (result.status) {
      case "started":
        setStartedDraft(result.draftDeadline);
        setPopup("started");
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "forbidden":
        window.alert("제안한 학생만 작업을 시작할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "notFound":
      case "notAvailable":
        setStartError("notAvailable");
        break;
      default:
        setStartError("retry");
    }
  };

  const decline = async () => {
    if (jobId === undefined || inFlight.current) return;
    inFlight.current = true;
    const id = ++requestId.current;
    setDeclining(true);
    setStartError(null);
    const result = await sendWorkDecline(jobId);
    inFlight.current = false;
    if (id !== requestId.current) return;
    setDeclining(false);
    setPopup("none");

    switch (result.status) {
      case "declined":
        navigate(STUDENT_PATHS.activity("proposals"), { replace: true });
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "forbidden":
        window.alert("제안한 학생만 의뢰서를 거절할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "notFound":
      case "notAvailable":
        setStartError("declineNotAvailable");
        break;
      default:
        setStartError("retry");
    }
  };

  const paidOn = agreement?.paidAt ? todayIsoDate(new Date(agreement.paidAt)) : undefined;
  const photos = proposal?.referenceImageUrls ?? [];
  const message = agreement?.messageToStudent?.trim();

  return (
    <SubScreen
      title="작업 시작"
      onBack={back}
      footer={
        proposal &&
        agreement && (
          <>
            {startError && (
              <p className="student-detail__send-error" role="alert">
                {START_ERROR_TEXT[startError]}
              </p>
            )}
            <div className="student-detail__actions">
              <Button variant="secondary" onClick={() => setPopup("decline")}>
                이 조건은 어려워요
              </Button>
              <Button tone="student" disabled={!agreed || starting} onClick={() => void start()}>
                {starting ? "시작하는 중..." : "동의하고 작업 시작하기"}
              </Button>
            </div>
          </>
        )
      }
    >
      {load.status !== "loaded" && (
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="의뢰서를 불러오는 중이에요"
          errorText="의뢰서를 불러오지 못했어요"
          onRetry={reload}
        />
      )}

      {proposal && agreement && (
        <div className="student-detail">
          <FlowBar tone="student" steps={flowSteps("제안", 1, "동의해 주세요")} />

          <h2 className="student-detail__headline">사장님이 제안을 받아들였어요</h2>

          <TurnNotice
            tone="student"
            title="의뢰서 조건을 확인하고 동의해 주세요"
            description="동의하면 바로 작업이 시작돼요"
          />

          <StoreBox
            name={proposal.storeName}
            address={storeAddressText(proposal.storeAddress)}
            note={paidOn ? `의뢰서 ${formatMonthDay(paidOn)} 도착` : undefined}
          />

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">사장님이 보낸 의뢰서</h2>
            <div className="student-work__request">
              <div className="student-work__request-head">
                <WorkKindIcon kind="request" size={22} />
                <strong>{proposal.title}</strong>
              </div>
              <InfoRows
                size="large"
                rows={[
                  {
                    label: "작업비",
                    // 사장님이 희망 작업비와 다르게 정했으면 둘을 함께 보인다
                    value:
                      agreement.budget === proposal.proposedFee ? (
                        formatWon(agreement.budget)
                      ) : (
                        <>
                          <span className="student-work__wish">희망 {formatWon(proposal.proposedFee)} → </span>
                          {formatWon(agreement.budget)}
                        </>
                      ),
                  },
                  { label: "초안 마감", value: formatMonthDayWeekday(agreement.draftDeadline) },
                  { label: "최종 마감", value: formatMonthDayWeekday(agreement.finalDeadline) },
                  { label: "수정", value: `${agreement.revisionCount}회` },
                ]}
              />
            </div>
          </section>

          {message && <NoteBox title={`${proposal.storeName} 사장님의 한마디`} body={message} />}

          <div className="student-detail__band" aria-hidden="true" />

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">내가 보낸 제안서</h2>
            <div className="student-work__proposal">
              <p className="student-work__label">손님 눈으로 본 문제</p>
              <p className="student-detail__text">{proposal.customerProblem}</p>
              <p className="student-work__label">이렇게 바꿔 드릴게요</p>
              <p className="student-detail__text">{proposal.proposedSolution}</p>
              <p className="student-work__label">작업계획서</p>
              <WorkPlan plan={proposal.workPlan} />
              <p className="student-work__label">희망 작업비 · 예상 기간</p>
              <p className="student-detail__text">
                {formatWon(proposal.proposedFee)} · {expectedDaysText(proposal.draftDays, proposal.finalDays)}
              </p>
              {photos.length > 0 && <ReferencePhotos urls={photos} />}
            </div>
          </section>

          <section className="student-detail__section">
            <h2 className="student-detail__section-title">시작 전에 약속해요</h2>
            <NumberedSteps
              variant="card"
              steps={[
                { title: "마감일까지 결과물을 낼게요", description: "작업비는 골목인턴이 보관하고, 완료되면 정산돼요" },
                { title: `수정 요청은 정한 횟수(${agreement.revisionCount}회)만큼 반영할게요` },
                { title: "연락 없이 마감을 넘기면 노쇼로 기록돼요" },
              ]}
            />
          </section>

          <AgreementCheckbox
            tone="student"
            checked={agreed}
            onChange={setAgreed}
          />
        </div>
      )}

      <Dialog
        open={popup === "started"}
        image="doneStudent"
        title="작업을 시작했어요"
        description={`초안은 ${formatMonthDay(startedDraft ?? agreement?.draftDeadline ?? todayIsoDate())}까지 제출해 주세요.\n채팅방이 열렸어요. 사장님께도 알릴게요.`}
        actions={
          <Button
            tone="student"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.activity("inProgress"), { replace: true })}
          >
            확인
          </Button>
        }
      />
      <Dialog
        open={popup === "decline"}
        image="warningStudent"
        title="의뢰서를 거절할까요?"
        description={"사장님께 거절 알림이 가고,\n맡겨 둔 작업비는 사장님께 모두 돌아가요."}
        onClose={() => setPopup("none")}
        actions={
          <>
            <Button tone="student" fullWidth disabled={declining} onClick={() => void decline()}>
              {declining ? "거절하는 중..." : "거절하기"}
            </Button>
            <Button variant="secondary" fullWidth disabled={declining} onClick={() => setPopup("none")}>
              돌아가기
            </Button>
          </>
        }
      />
    </SubScreen>
  );
}

export default StudentProposalStartPage;
