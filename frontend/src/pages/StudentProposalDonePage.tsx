import { useLocation, useNavigate } from "react-router-dom";
import { Button, DoneScreen } from "../components";
import { STUDENT_PATHS, readProposalDoneState } from "../features/student";

/**
 * 피그마 「제안 보내기 - 완료」. 「확인」은 방금 보낸 제안 상세로, 뒤로 가기 없이.
 * 주소로 바로 들어와 제안 id 가 없으면 내 활동 (보낸 제안) 으로 간다.
 */
function StudentProposalDonePage() {
  const navigate = useNavigate();
  const proposalId = readProposalDoneState(useLocation().state);

  return (
    <DoneScreen
      image="doneStudent"
      title="제안을 보냈어요"
      description={"사장님이 확인하면\n알림으로 알려드릴게요"}
      action={
        <Button
          tone="student"
          fullWidth
          onClick={() =>
            navigate(
              proposalId === undefined
                ? STUDENT_PATHS.activity("proposals")
                : STUDENT_PATHS.proposal(String(proposalId)),
              { replace: true },
            )
          }
        >
          확인
        </Button>
      }
    />
  );
}

export default StudentProposalDonePage;
