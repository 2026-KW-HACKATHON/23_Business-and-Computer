import { useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Checkbox,
  Dialog,
  FormField,
  SubScreen,
  TextAreaField,
  WorkKindIcon,
} from "../components";
import { OWNER_PATHS, OwnerMissing, useOwnerRequest } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./OwnerWorkCancelPage.css";

/**
 * 피그마 「의뢰 취소 - 이유·남길 말 (모집 중)」. 학생을 고르기 전이라 돌려받을 작업비는 없고,
 * 취소 이유와 지원한 학생에게 남길 말은 둘 다 적어야 한다 (POST /jobs/{id}/cancel).
 */
function OwnerRequestCancelPage() {
  const { requestId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.request(requestId));
  const request = useOwnerRequest(requestId);
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [canceled, setCanceled] = useState(false);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 취소한다
  const canceledRef = useRef(false);

  if (!request) return <OwnerMissing title="의뢰 취소" onBack={back} />;

  const applicantCount = request.applicants.length;
  const ready = reason.trim() !== "" && message.trim() !== "" && agreed;

  const cancel = () => {
    if (canceledRef.current) return;
    canceledRef.current = true;
    setCanceled(true);
  };

  return (
    <SubScreen
      title="의뢰 취소"
      onBack={back}
      footer={
        <Button fullWidth disabled={!ready || canceled} onClick={cancel}>
          의뢰 취소하기
        </Button>
      }
    >
      <div className="owner-cancel">
        <section className="owner-cancel__work">
          <div className="owner-cancel__work-head">
            <WorkKindIcon kind="request" size={22} />
            <h2 className="owner-cancel__work-title">{request.title}</h2>
          </div>
          <p className="owner-cancel__meta">
            모집 중 · {applicantCount === 0 ? "아직 지원자 없음" : `지원자 ${applicantCount}명`} ·
            작업비 {formatWon(request.budget)}
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
