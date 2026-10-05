import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Checkbox,
  Dialog,
  FormField,
  ReportSheet,
  SubScreen,
  TextAreaField,
  TextButton,
  WorkKindIcon,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  RefundBreakdown,
  startReward,
  useOwnerWork,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./OwnerWorkCancelPage.css";

/**
 * 피그마 「작업 취소 - 이유·환불 금액」. 학생이 작업을 시작했으면 착수 보상 20%를 뺀
 * 금액을 돌려받고, 결과물을 받은 뒤에는 취소할 수 없다 (노션 「취소·환불 정책」).
 * 취소 이유와 학생에게 남길 말은 둘 다 적어야 한다 (POST /jobs/{id}/cancel).
 */
function OwnerWorkCancelPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.activity("inProgress"));
  const work = useOwnerWork(workId);
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [canceled, setCanceled] = useState(false);

  if (!work) return <OwnerMissing title="작업 취소" onBack={back} />;

  const cancelable = work.status === "inProgress";
  const reward = startReward(work.budget);
  const stage = work.revisionCount > 0 ? "수정안 제작 중" : "초안 제작 중";

  return (
    <SubScreen
      title="작업 취소"
      onBack={back}
      footer={
        <Button
          fullWidth
          disabled={!cancelable || reason.trim() === "" || message.trim() === "" || !agreed}
          onClick={() => setCanceled(true)}
        >
          작업 취소하기
        </Button>
      }
    >
      <div className="owner-cancel">
        <section className="owner-cancel__work">
          <div className="owner-cancel__work-head">
            <WorkKindIcon kind={work.kind} size={22} />
            <h2 className="owner-cancel__work-title">{work.title}</h2>
          </div>
          <p className="owner-cancel__meta">
            {work.student.name} 학생 · {cancelable ? stage : "결과물 도착"} · 작업비{" "}
            {formatWon(work.budget)}
          </p>
        </section>

        <div className="owner-cancel__intro">
          <h2 className="owner-cancel__title">작업을 취소할까요?</h2>
          <p className="owner-cancel__description">
            {cancelable
              ? "학생이 이미 작업을 시작해서, 착수 보상 20%를 뺀 금액을 돌려받아요."
              : "결과물을 받은 뒤에는 취소할 수 없어요. 수정 요청이나 완료 확인만 할 수 있어요."}
          </p>
        </div>

        {cancelable && (
          <>
            <FormField label="취소 이유" wrapsInput>
              <TextAreaField
                value={reason}
                maxLength={300}
                placeholder="예: 작업이 필요없어졌어요."
                onChange={setReason}
              />
            </FormField>

            <FormField label="학생에게 남길 말" wrapsInput>
              <TextAreaField
                value={message}
                maxLength={300}
                placeholder="예: 가게 사정으로 미루게 됐어요. 죄송해요."
                onChange={setMessage}
              />
            </FormField>

            <RefundBreakdown
              title="돌려받는 금액"
              amount={work.budget}
              reward={reward}
              note="학생이 아직 시작하지 않았으면 전액을 돌려받아요. 결과물을 받은 뒤에는 취소할 수 없어요."
            />
          </>
        )}

        <div className="owner-cancel__report">
          <span>학생이 연락이 안 되거나 약속을 안 지켰나요?</span>
          <TextButton onClick={() => setReportOpen(true)}>학생 문제 신고</TextButton>
        </div>

        {cancelable && (
          <Checkbox
            checked={agreed}
            onChange={setAgreed}
            label="취소 후에는 되돌릴 수 없다는 걸 확인했어요"
          />
        )}
      </div>

      <ReportSheet open={reportOpen} workTitle={work.title} onClose={() => setReportOpen(false)} />

      <Dialog
        open={canceled}
        image="doneOwner"
        title="작업을 취소했어요"
        description={`${formatWon(work.budget - reward)}을 결제한 수단으로 돌려드려요.\n${work.student.name} 학생에게는 착수 보상 ${formatWon(reward)}이 가요.`}
        actions={
          <Button fullWidth onClick={() => navigate(OWNER_PATHS.activity("done"), { replace: true })}>
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default OwnerWorkCancelPage;
