import { Button, Dialog } from "../../../components";
import type { ProposalReject } from "../hooks/useProposalReject";
import "./ProposalRejectDialogs.css";

interface ProposalRejectDialogsProps {
  reject: ProposalReject;
  /** 완료 팝업의 「확인」 · 바깥. 내 활동은 팝업만 닫고, 상세는 이전 화면(홈이나 내 활동)으로 */
  onDone: () => void;
}

/**
 * 받은 제안 거절 팝업 둘 (ADR 0033). 피그마 「제안 거절 확인 (팝업)」 → 「제안 거절 완료 (팝업)」.
 * 실패 안내는 확인 팝업 설명 아래에 보인다.
 */
function ProposalRejectDialogs({ reject, onDone }: ProposalRejectDialogsProps) {
  return (
    <>
      <Dialog
        open={reject.step === "confirm"}
        title="제안을 거절할까요?"
        description="거절하면 되돌릴 수 없어요."
        onClose={reject.close}
        actions={
          <>
            <Button fullWidth disabled={reject.rejecting} onClick={() => void reject.confirm()}>
              {reject.rejecting ? "거절하는 중..." : "거절하기"}
            </Button>
            <Button variant="secondary" fullWidth disabled={reject.rejecting} onClick={reject.close}>
              돌아가기
            </Button>
          </>
        }
      >
        {reject.error && (
          <p className="proposal-reject__error" role="alert">
            {reject.error}
          </p>
        )}
      </Dialog>
      <Dialog
        open={reject.step === "done"}
        image="doneOwner"
        title="학생의 제안을 거절했어요"
        description="학생에게는 성사되지 않은 제안으로 보여요."
        onClose={onDone}
        actions={
          <Button fullWidth onClick={onDone}>
            확인
          </Button>
        }
      />
    </>
  );
}

export default ProposalRejectDialogs;
