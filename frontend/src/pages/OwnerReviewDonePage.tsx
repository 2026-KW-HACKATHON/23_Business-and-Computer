import { useLocation, useNavigate } from "react-router-dom";
import { Button, DoneScreen, StarRating } from "../components";
import { OWNER_PATHS } from "../features/owner";
import { studentTitle } from "../lib/korean";

/** 피그마 「후기 완료 / 정산 완료」. 남긴 별점을 보여 주고 홈으로. 학생 이름 · 별점은 후기 작성에서 받는다 */
function OwnerReviewDonePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as { rating?: number; studentName?: string } | null;
  const rating = state?.rating;
  const name = state?.studentName;
  const studentName = name ? `${studentTitle(name)}에게` : "학생에게";

  return (
    <DoneScreen
      image="doneOwnerThumbsUp"
      title="후기를 남겼어요"
      description={`${studentName}\n작업비를 전달했어요`}
      action={
        <Button fullWidth onClick={() => navigate(OWNER_PATHS.home, { replace: true })}>
          홈으로
        </Button>
      }
    >
      {rating !== undefined && <StarRating value={rating} size={40} />}
    </DoneScreen>
  );
}

export default OwnerReviewDonePage;
