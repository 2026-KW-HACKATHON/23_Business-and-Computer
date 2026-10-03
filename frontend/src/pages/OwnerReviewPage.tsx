import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppImage, Button, Chip, StarRating, SubScreen, TextButton } from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  TextAreaField,
  markOwnerWorkReviewed,
  useOwnerWork,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import "./OwnerReviewPage.css";

const RATING_LABELS = ["", "별로예요", "아쉬워요", "보통이에요", "좋아요", "최고예요"];

const GOOD_POINTS = ["결과물이 좋아요", "마감을 잘 지켜요", "소통이 빨라요", "친절해요", "수정을 잘 반영해요"];

/** 피그마 「후기 작성」. 완료 확인 직후 들어온다. 별점은 꼭 골라야 한다 */
function OwnerReviewPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const work = useOwnerWork(workId);
  const [rating, setRating] = useState(0);
  const [points, setPoints] = useState<string[]>([]);
  const [text, setText] = useState("");

  if (!work) return <OwnerMissing title="후기 작성" onBack={back} />;

  const togglePoint = (point: string) =>
    setPoints((prev) => (prev.includes(point) ? prev.filter((p) => p !== point) : [...prev, point]));

  return (
    <SubScreen
      title="후기 작성"
      onBack={back}
      footer={
        <div className="owner-review__actions">
          <Button
            variant="secondary"
            className="owner-review__skip"
            onClick={() => navigate(OWNER_PATHS.home, { replace: true })}
          >
            건너뛰기
          </Button>
          <Button
            className="owner-review__submit"
            disabled={rating === 0}
            onClick={() => {
              markOwnerWorkReviewed(work.id);
              navigate(OWNER_PATHS.workReviewDone(work.id), { replace: true, state: { rating } });
            }}
          >
            후기 남기기
          </Button>
        </div>
      }
    >
      <div className="owner-review">
        <section className="owner-review__done">
          <AppImage name="doneStudentV" width={72} />
          <h2 className="owner-review__done-title">작업이 끝났어요</h2>
          <p className="owner-review__done-text">
            {"작업비가 학생에게 정산돼요.\n사장님은 원본 파일을 받을 수 있어요"}
          </p>
          <TextButton onClick={() => navigate(OWNER_PATHS.workResult(work.id))}>
            결과물 받기
          </TextButton>
        </section>

        <section className="owner-review__form">
          <div className="owner-review__head">
            <h2 className="owner-review__title">{work.student.name} 학생은 어땠나요?</h2>
            <p className="owner-review__description">
              남겨 주신 후기는 다른 사장님들이 학생을 고를 때 도움이 돼요
            </p>
          </div>

          <div className="owner-review__rating">
            <StarRating value={rating} onChange={setRating} />
            <span className="owner-review__rating-label">
              {rating > 0 ? `${rating}.0 · ${RATING_LABELS[rating]}` : "별점을 골라 주세요"}
            </span>
          </div>

          <div className="owner-review__points">
            <h3 className="owner-review__points-title">좋았던 점</h3>
            <div className="owner-review__chips">
              {GOOD_POINTS.map((point) => (
                <Chip
                  key={point}
                  variant="outlined"
                  tone="owner"
                  label={point}
                  selected={points.includes(point)}
                  onClick={() => togglePoint(point)}
                />
              ))}
            </div>
          </div>

          <TextAreaField
            value={text}
            maxLength={300}
            placeholder="학생에게 남길 말을 적어 주세요 (선택)"
            onChange={setText}
          />
        </section>
      </div>
    </SubScreen>
  );
}

export default OwnerReviewPage;
