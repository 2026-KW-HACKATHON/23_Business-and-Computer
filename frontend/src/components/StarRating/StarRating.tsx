import "./StarRating.css";

interface StarRatingProps {
  /** 0 ~ 5 */
  value: number;
  /** 넣으면 별을 눌러 고를 수 있다 */
  onChange?: (value: number) => void;
  /** 별 크기(px) */
  size?: number;
}

const STARS = [1, 2, 3, 4, 5];

/** 노란 별 5개. 후기 작성(고르기)과 후기 완료(보여 주기)에서 쓴다 */
function StarRating({ value, onChange, size = 34 }: StarRatingProps) {
  const starClass = (star: number) =>
    `star-rating__star${star <= value ? " star-rating__star--on" : ""}`;

  if (!onChange) {
    return (
      <span className="star-rating" role="img" aria-label={`별점 ${value}점`} style={{ fontSize: size }}>
        {STARS.map((star) => (
          <span key={star} className={starClass(star)} aria-hidden="true">
            ★
          </span>
        ))}
      </span>
    );
  }

  return (
    <span className="star-rating" role="radiogroup" aria-label="별점" style={{ fontSize: size }}>
      {STARS.map((star) => (
        <button
          key={star}
          type="button"
          role="radio"
          aria-checked={star === value}
          aria-label={`${star}점`}
          className={starClass(star)}
          onClick={() => onChange(star)}
        >
          ★
        </button>
      ))}
    </span>
  );
}

export default StarRating;
