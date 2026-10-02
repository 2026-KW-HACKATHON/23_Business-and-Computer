import { BottomSheet, Button, NumberedSteps } from "../../../components";
import { formatWon } from "../../../lib/money";
import { startReward } from "../lib/payment";
import "./RefundGuideSheet.css";

interface RefundGuideSheetProps {
  open: boolean;
  /** 계산 예시에 쓰는 작업비 */
  amount: number;
  onClose: () => void;
}

/** 피그마 「취소·환불 안내 (팝업)」. 안전결제의 「자세히 보기」 */
function RefundGuideSheet({ open, amount, onClose }: RefundGuideSheetProps) {
  const reward = startReward(amount);
  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="취소하면 이렇게 돌려받아요"
      description="결과물을 받기 전까지만 취소할 수 있어요"
      footer={
        <Button fullWidth onClick={onClose}>
          확인
        </Button>
      }
    >
      <div className="refund-guide">
        <NumberedSteps
          variant="card"
          steps={[
            { title: "작업 시작 전", description: "사장님이 바로 취소할 수 있어요", trailing: "전액 환불" },
            {
              title: "작업 중 (결과물 받기 전)",
              description: "학생에게 착수 보상 20%를 드려요",
              trailing: "80% 환불",
            },
            {
              title: "결과물을 받은 뒤",
              description: "수정 요청이나 완료 확인만 할 수 있어요",
              trailing: "취소 불가",
              danger: true,
            },
            {
              title: "학생 문제로 취소할 때",
              description: "노쇼·연락두절·마감 초과·빈 결과물\n메일로 신고하면 운영자가 판단",
              trailing: "확인되면 전액",
              mark: "!",
            },
          ]}
        />
        <div className="refund-guide__example">
          <strong>작업비 {formatWon(amount)} · 작업 중에 취소하면</strong>
          <dl>
            <div className="refund-guide__row">
              <dt>학생 착수 보상 (20%)</dt>
              <dd>{formatWon(reward)}</dd>
            </div>
            <div className="refund-guide__row refund-guide__row--total">
              <dt>사장님이 돌려받는 금액</dt>
              <dd>{formatWon(amount - reward)}</dd>
            </div>
          </dl>
        </div>
        <p className="refund-guide__note">
          해커톤 기간에는 수수료가 없어요. 결과물을 받고 7일 동안 답이 없으면 자동으로 완료돼요.
        </p>
      </div>
    </BottomSheet>
  );
}

export default RefundGuideSheet;
