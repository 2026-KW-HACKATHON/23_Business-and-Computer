import { useEffect, useRef, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Checkbox,
  Dialog,
  FormField,
  LoadNotice,
  SubScreen,
  TextAreaField,
  WorkKindIcon,
} from "../components";
import { landingPath } from "../features/auth";
import { useJobDetail } from "../features/explore";
import {
  OWNER_PATHS,
  OwnerMissing,
  parsePositiveId,
  sendJobCancel,
  useJobApplications,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./OwnerWorkCancelPage.css";

type CancelError = "notAvailable" | "invalidInput" | "retry";

const CANCEL_ERROR_TEXT: Record<CancelError, string> = {
  notAvailable: "지금은 취소할 수 없는 의뢰예요. 의뢰 상태를 다시 확인해 주세요",
  invalidInput: "입력한 내용을 다시 확인해 주세요",
  retry: "잠시 후 다시 시도해 주세요",
};

/**
 * 피그마 「의뢰 취소 - 이유·남길 말 (모집 중)」. 학생을 고르기 전이라 돌려받을 작업비는 없고,
 * 취소 이유와 지원한 학생에게 남길 말은 둘 다 적어야 한다 (POST /jobs/{id}/cancel, ADR 0030).
 * 모집 중이 아닌 의뢰는 보낸 의뢰서로 돌려보낸다.
 */
function OwnerRequestCancelPage() {
  const { requestId } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.request(requestId ?? ""));
  const jobId = parsePositiveId(requestId);
  const { load, reload } = useJobDetail(requestId);
  const job = load.status === "loaded" ? load.job : undefined;
  const { load: applicants } = useJobApplications(job?.status === "OPEN" ? jobId : undefined, "LATEST");
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [sending, setSending] = useState(false);
  const [canceled, setCanceled] = useState(false);
  const [cancelError, setCancelError] = useState<CancelError | null>(null);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 취소한다
  const inFlight = useRef(false);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestRef = useRef(0);

  useEffect(() => {
    const latest = requestRef;
    return () => {
      latest.current += 1;
    };
  }, []);

  if (load.status === "notFound") return <OwnerMissing title="의뢰 취소" onBack={back} />;
  if (job && job.status !== "OPEN" && !canceled) {
    return <Navigate to={OWNER_PATHS.request(String(job.id))} replace />;
  }

  const applicantCount = applicants.status === "loaded" ? applicants.data.applicantCount : undefined;
  const ready = reason.trim() !== "" && message.trim() !== "" && agreed;

  const cancel = async () => {
    if (jobId === undefined || inFlight.current) return;
    inFlight.current = true;
    const id = ++requestRef.current;
    setSending(true);
    setCancelError(null);
    const result = await sendJobCancel(jobId, reason, message);
    inFlight.current = false;
    if (id !== requestRef.current) return;
    setSending(false);
    switch (result.status) {
      case "canceled":
        setCanceled(true);
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "forbidden":
        window.alert("내 의뢰만 취소할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "notFound":
      case "notAvailable":
        setCancelError("notAvailable");
        break;
      case "invalidInput":
        setCancelError("invalidInput");
        break;
      default:
        setCancelError("retry");
    }
  };

  return (
    <SubScreen
      title="의뢰 취소"
      onBack={back}
      footer={
        job && (
          <>
            {cancelError && (
              <p className="owner-cancel__send-error" role="alert">
                {CANCEL_ERROR_TEXT[cancelError]}
              </p>
            )}
            <Button fullWidth disabled={!ready || sending || canceled} onClick={() => void cancel()}>
              {sending ? "취소하는 중..." : "의뢰 취소하기"}
            </Button>
          </>
        )
      }
    >
      {load.status !== "loaded" && (
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="의뢰를 불러오는 중이에요"
          errorText="의뢰를 불러오지 못했어요"
          onRetry={reload}
        />
      )}

      {job && (
        <div className="owner-cancel">
          <section className="owner-cancel__work">
            <div className="owner-cancel__work-head">
              <WorkKindIcon kind="request" size={22} />
              <h2 className="owner-cancel__work-title">{job.title}</h2>
            </div>
            <p className="owner-cancel__meta">
              {[
                "모집 중",
                applicantCount === undefined
                  ? undefined
                  : applicantCount === 0
                    ? "아직 지원자 없음"
                    : `지원자 ${applicantCount}명`,
                `작업비 ${formatWon(job.budget)}`,
              ]
                .filter(Boolean)
                .join(" · ")}
            </p>
          </section>

          <div className="owner-cancel__intro">
            <h2 className="owner-cancel__title">의뢰를 취소할까요?</h2>
            <p className="owner-cancel__description">
              아직 학생을 고르기 전이라 결제한 작업비가 없어요. 지원한 학생에게는 취소 안내와 남긴
              말이 가요.
            </p>
          </div>

          <FormField label="취소 이유" wrapsInput>
            <TextAreaField
              value={reason}
              maxLength={300}
              placeholder="예: 가게 사정으로 이번에는 진행하지 않기로 했어요."
              onChange={setReason}
            />
          </FormField>

          <FormField label="학생에게 남길 말" wrapsInput>
            <TextAreaField
              value={message}
              maxLength={300}
              placeholder="예: 지원해 주셔서 감사해요. 다음에 다시 의뢰할게요."
              onChange={setMessage}
            />
          </FormField>

          <Checkbox
            checked={agreed}
            onChange={setAgreed}
            label="취소 후에는 되돌릴 수 없다는 걸 확인했어요"
          />
        </div>
      )}

      <Dialog
        open={canceled}
        image="doneOwner"
        title="의뢰를 취소했어요"
        actions={
          <Button fullWidth onClick={() => navigate(OWNER_PATHS.activity("sent"), { replace: true })}>
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default OwnerRequestCancelPage;
