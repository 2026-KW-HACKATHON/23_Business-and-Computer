import { useLocation, useNavigate, useParams } from "react-router-dom";
import { Button, DoneScreen, StarRating } from "../components";
import { OWNER_PATHS, useOwnerWork } from "../features/owner";

/** 피그마 「후기 완료 / 정산 완료」. 남긴 별점을 보여 주고 홈으로 */
function OwnerReviewDonePage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const work = useOwnerWork(workId);
  const rating = (location.state as { rating?: number } | null)?.rating;
  const studentName = work ? `${work.student.name} 학생에게` : "학생에게";

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
