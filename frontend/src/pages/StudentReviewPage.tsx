import { useNavigate, useParams } from "react-router-dom";
import { AppImage, Button, LabelChip, LoadNotice, StarRating, SubScreen, TextButton } from "../components";
import { REVIEW_POINT_LABEL, STUDENT_PATHS, StudentMissing, useReceivedReview } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/** 별점 문구 (5점 = 최고예요) */
const RATING_WORDS = ["", "아쉬워요", "그저 그래요", "괜찮아요", "좋아요", "최고예요"];

/** 받은 후기 한 장에 보이는 것 */
interface ReviewView {
  /** 내 결과물 주소의 id */
  workId: string;
  storeName: string;
  workTitle: string;
  date: string;
  rating: number;
  points: string[];
  text?: string;
}

/**
 * 피그마 「받은 후기 보기」. 작업이 끝나 정산된 뒤 사장님이 남긴 후기.
 * 서버 작업(GET /jobs/{id}/review, ADR 0042). 주소의 id 가 숫자가 아니면 찾을 수 없음.
 */
function StudentReviewPage() {
  const { workId = "" } = useParams();
  const back = useBack(STUDENT_PATHS.home);
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <JobReview jobId={jobId} />
  ) : (
    <StudentMissing title="받은 후기" onBack={back} />
  );
}

/** 서버 작업에서 받은 후기 */
function JobReview({ jobId }: { jobId: number }) {
  const back = useBack(STUDENT_PATHS.home);
  const { load, reload } = useReceivedReview(jobId);

  if (load.status === "notFound") {
    return <StudentMissing title="받은 후기" onBack={back} message="아직 받은 후기가 없어요" />;
  }
  if (load.status !== "loaded") {
    return (
      <SubScreen title="받은 후기" onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="후기를 불러오는 중이에요"
          errorText="후기를 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  const review = load.data;
  return (
    <ReviewScreen
      onBack={back}
      review={{
        workId: String(jobId),
        storeName: review.storeName?.trim() || "가게",
        workTitle: review.jobTitle,
        date: review.createdAt,
        rating: review.rating,
        points: review.positivePoints.map((point) => REVIEW_POINT_LABEL[point]),
        text: review.content?.trim() || undefined,
      }}
    />
  );
}

function ReviewScreen({ review, onBack }: { review: ReviewView; onBack: () => void }) {
  const navigate = useNavigate();
  return (
    <SubScreen
      title="받은 후기"
      onBack={onBack}
      footer={
        <Button tone="student" fullWidth onClick={onBack}>
          확인
        </Button>
      }
    >
      <div className="student-detail">
        <div className="student-work__done">
          <AppImage name="doneStudent" width={120} alt="" />
          <h2 className="student-work__done-title">작업이 끝났어요</h2>
          <p className="student-work__done-text">
            작업비가 정산됐어요.
            <br />
            받은 후기와 결과물은 내 프로필에 쌓여요.
          </p>
          <TextButton onClick={() => navigate(STUDENT_PATHS.workResult(review.workId))}>내 결과물 보기</TextButton>
        </div>

        <section className="student-work__review-card">
          <h2 className="student-detail__section-title">{review.storeName} 사장님이 남긴 후기</h2>
          <p className="student-work__review-meta">
            {review.workTitle} · {formatMonthDay(review.date)}
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
          {review.text && <p className="student-detail__text">{review.text}</p>}
        </section>
      </div>
    </SubScreen>
  );
}

export default StudentReviewPage;
