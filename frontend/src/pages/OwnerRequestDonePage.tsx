import { useNavigate } from "react-router-dom";
import { Button, DoneScreen } from "../components";
import { OWNER_PATHS } from "../features/owner";

/** 피그마 「의뢰 등록 - 완료」. 「확인」은 내 의뢰 (보낸 의뢰) 로, 뒤로 가기 없이 */
function OwnerRequestDonePage() {
  const navigate = useNavigate();

  return (
    <DoneScreen
      image="doneOwner"
      title="의뢰를 보냈어요"
      description={"학생들이 의뢰에 지원하면\n알림으로 알려드릴게요"}
      action={
        <Button fullWidth onClick={() => navigate(OWNER_PATHS.activity("sent"), { replace: true })}>
          확인
        </Button>
      }
    />
  );
}

export default OwnerRequestDonePage;
