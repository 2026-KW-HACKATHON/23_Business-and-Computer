import { useParams } from "react-router-dom";
import { Button, Chip, LoadNotice, StarRating, SubScreen } from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  REVIEW_POINTS,
  REVIEW_RATING_LABELS,
  parsePositiveId,
  useJobResult,
  useOwnerJobReview,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { studentTitle } from "../lib/korean";
import "./OwnerReviewPage.css";

/**
 * 피그마 「남긴 후기 보기 (사장님)」 (ADR 0045). 작업 이력의 「후기」 줄. 후기 작성 화면을 읽기만 한다:
 * 고른 별점 · 좋았던 점 · 쓴 글 (GET /jobs/{id}/review). 학생 이름은 결과물(GET /jobs/{id}/result)에서 읽는다
 */
function OwnerReviewViewPage() {
  const { workId } = useParams();
  const jobId = parsePositiveId(workId);
  const back = useBack(OWNER_PATHS.chats);
  const { load, reload } = useOwnerJobReview(jobId);
  const { load: resultLoad } = useJobResult(jobId);

  if (load.status === "notFound" || load.status === "closed") {
    return <OwnerMissing title="남긴 후기" onBack={back} message="아직 남긴 후기가 없어요" />;
  }
  if (load.status !== "loaded") {
    return (
      <SubScreen title="남긴 후기" onBack={back}>
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
  const name = resultLoad.status === "loaded" ? resultLoad.data.studentName.trim() : "";
  const text = review.content?.trim();

  return (
    <SubScreen
      title="남긴 후기"
      onBack={back}
      footer={
        <Button fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      <div className="owner-review">
        <section className="owner-review__form">
          <div className="owner-review__head">
            <h2 className="owner-review__title">{name ? `${studentTitle(name)}에게 남긴 후기` : "남긴 후기"}</h2>
            <p className="owner-review__description">다른 사장님들이 학생을 고를 때 이 후기를 봐요</p>
          </div>

          <div className="owner-review__rating">
            <StarRating value={review.rating} />
            <span className="owner-review__rating-label">
              {`${review.rating}.0 · ${REVIEW_RATING_LABELS[review.rating] ?? ""}`}
            </span>
          </div>

          <div className="owner-review__points">
            <h3 className="owner-review__points-title">좋았던 점</h3>
            <div className="owner-review__chips">
              {REVIEW_POINTS.map(({ label, value }) => (
                <Chip
                  key={value}
                  variant="outlined"
                  tone="owner"
                  label={label}
                  selected={review.positivePoints.includes(value)}
                  tabIndex={-1}
                  aria-disabled
                />
              ))}
            </div>
          </div>

          {text && (
            <div className="request-field__textarea-box">
              <p className="owner-review__written">{text}</p>
            </div>
          )}
        </section>
      </div>
    </SubScreen>
  );
}

export default OwnerReviewViewPage;
