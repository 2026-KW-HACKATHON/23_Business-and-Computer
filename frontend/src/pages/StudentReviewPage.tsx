import { useNavigate, useParams } from "react-router-dom";
import { AppImage, Button, LabelChip, StarRating, SubScreen, TextButton } from "../components";
import { STUDENT_PATHS, StudentMissing, useStudentWork } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/** 별점 문구 (5점 = 최고예요) */
const RATING_WORDS = ["", "아쉬워요", "그저 그래요", "괜찮아요", "좋아요", "최고예요"];

/** 피그마 「받은 후기 보기」. 작업이 끝나 정산된 뒤 사장님이 남긴 후기 */
function StudentReviewPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const work = useStudentWork(workId);

  if (!work?.review) return <StudentMissing title="받은 후기" onBack={back} message="아직 받은 후기가 없어요" />;
  const { review } = work;

  return (
    <SubScreen
      title="받은 후기"
      onBack={back}
      footer={
        <Button tone="student" fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      <div className="student-detail">
        <div className="student-work__done">
          <AppImage name="doneStudent" width={120} alt="" />
          <h2 className="student-work__done-title">작업이 끝났어요</h2>
          <p className="student-work__done-text">
            작업비가 정산됐어요. 받은 후기와 결과물은 내 프로필에 쌓여요.
          </p>
          <TextButton onClick={() => navigate(STUDENT_PATHS.workResult(work.id))}>내 결과물 보기</TextButton>
        </div>

        <section className="student-work__review-card">
          <h2 className="student-detail__section-title">{work.store.name} 사장님이 남긴 후기</h2>
          <p className="student-work__review-meta">
            {work.title} · {formatMonthDay(review.date)}
          </p>
          <div className="student-work__stars">
            <StarRating value={review.rating} size={24} />
            <span>
              {review.rating.toFixed(1)} · {RATING_WORDS[Math.round(review.rating)]}
            </span>
          </div>
          {review.points.length > 0 && (
            <div className="student-work__points">
              <span className="student-work__label">좋았던 점</span>
              <div className="student-detail__chips">
                {review.points.map((point) => (
                  <LabelChip key={point} label={point} />
                ))}
              </div>
            </div>
          )}
          <p className="student-detail__text">{review.text}</p>
        </section>
      </div>
    </SubScreen>
  );
}

export default StudentReviewPage;
