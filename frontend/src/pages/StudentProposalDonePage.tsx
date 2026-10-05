import { useNavigate } from "react-router-dom";
import { Button, DoneScreen } from "../components";
import { STUDENT_PATHS } from "../features/student";

/** 피그마 「제안 보내기 - 완료」. 「확인」은 내 활동 (보낸 제안) 으로, 뒤로 가기 없이 */
function StudentProposalDonePage() {
  const navigate = useNavigate();

  return (
    <DoneScreen
      image="doneStudent"
      title="제안을 보냈어요"
      description={"사장님이 확인하면\n알림으로 알려드릴게요"}
      action={
        <Button
          tone="student"
          fullWidth
          onClick={() => navigate(STUDENT_PATHS.activity("proposals"), { replace: true })}
        >
          확인
        </Button>
      }
    />
  );
}

export default StudentProposalDonePage;
