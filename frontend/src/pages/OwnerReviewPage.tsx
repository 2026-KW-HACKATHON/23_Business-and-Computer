import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  Button,
  Chip,
  LoadNotice,
  StarRating,
  SubScreen,
  TextAreaField,
  TextButton,
} from "../components";
import { landingPath } from "../features/auth";
import {
  OWNER_PATHS,
  OwnerMissing,
  REVIEW_POINTS,
  REVIEW_RATING_LABELS,
  markOwnerWorkReviewed,
  parsePositiveId,
  sendJobReview,
  useJobResult,
  useOwnerWork,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { studentTitle } from "../lib/korean";
import "./OwnerReviewPage.css";

/**
 * 피그마 「후기 작성」. 완료 확인 직후 들어온다. 별점은 꼭 골라야 한다.
 * 주소의 id 가 숫자면 서버 작업(POST /jobs/{id}/reviews, ADR 0036), 아니면 샘플 작업.
 */
function OwnerReviewPage() {
  const { workId = "" } = useParams();
  const jobId = parsePositiveId(workId);
  return jobId !== undefined ? <JobReview jobId={jobId} /> : <SampleReview workId={workId} />;
}

/** 별점 · 좋았던 점 · 남길 말 (샘플 · 서버 작업이 같이 쓴다) */
function useReviewForm() {
  const [rating, setRating] = useState(0);
  const [points, setPoints] = useState<string[]>([]);
  const [text, setText] = useState("");
  const togglePoint = (point: string) =>
    setPoints((prev) => (prev.includes(point) ? prev.filter((p) => p !== point) : [...prev, point]));
  return { rating, setRating, points, togglePoint, text, setText };
}

/** 후기 작성 화면. 위에 「작업이 끝났어요」, 아래에 별점 · 좋았던 점 · 남길 말 */
function ReviewScreen({
  studentName,
  form,
  onResult,
  onSkip,
  submitLabel,
  submitDisabled,
  onSubmit,
  error,
}: {
  studentName: string;
  form: ReturnType<typeof useReviewForm>;
  onResult: () => void;
  onSkip: () => void;
  submitLabel: string;
  submitDisabled: boolean;
  onSubmit: () => void;
  error?: string | null;
}) {
  const { rating, setRating, points, togglePoint, text, setText } = form;
  return (
    <SubScreen
      title="후기 작성"
      onBack={onSkip}
      footer={
        <>
          {error && (
            <p className="owner-review__send-error" role="alert">
              {error}
            </p>
          )}
          <div className="owner-review__actions">
            <Button variant="secondary" className="owner-review__skip" onClick={onSkip}>
              건너뛰기
            </Button>
            <Button className="owner-review__submit" disabled={submitDisabled} onClick={onSubmit}>
              {submitLabel}
            </Button>
          </div>
        </>
      }
    >
      <div className="owner-review">
        <section className="owner-review__done">
          <AppImage name="doneStudentV" width={72} />
          <h2 className="owner-review__done-title">작업이 끝났어요</h2>
          <p className="owner-review__done-text">
            {"작업비가 학생에게 정산돼요.\n사장님은 원본 파일을 받을 수 있어요"}
          </p>
          <TextButton onClick={onResult}>결과물 받기</TextButton>
        </section>

        <section className="owner-review__form">
          <div className="owner-review__head">
            <h2 className="owner-review__title">{studentTitle(studentName)}은 어땠나요?</h2>
            <p className="owner-review__description">
              남겨 주신 후기는 다른 사장님들이 학생을 고를 때 도움이 돼요
            </p>
          </div>

          <div className="owner-review__rating">
            <StarRating value={rating} onChange={setRating} />
            <span className="owner-review__rating-label">
              {rating > 0 ? `${rating}.0 · ${REVIEW_RATING_LABELS[rating]}` : "별점을 골라 주세요"}
            </span>
          </div>

          <div className="owner-review__points">
            <h3 className="owner-review__points-title">좋았던 점</h3>
            <div className="owner-review__chips">
              {REVIEW_POINTS.map(({ label }) => (
                <Chip
                  key={label}
                  variant="outlined"
                  tone="owner"
                  label={label}
                  selected={points.includes(label)}
                  onClick={() => togglePoint(label)}
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

/** 샘플 작업의 후기 작성 */
function SampleReview({ workId }: { workId: string }) {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const work = useOwnerWork(workId);
  const form = useReviewForm();

  if (!work) return <OwnerMissing title="후기 작성" onBack={back} />;

  return (
    <ReviewScreen
      studentName={work.student.name}
      form={form}
      onResult={() => navigate(OWNER_PATHS.workResult(work.id))}
      onSkip={() => navigate(OWNER_PATHS.home, { replace: true })}
      submitLabel="후기 남기기"
      submitDisabled={form.rating === 0}
      onSubmit={() => {
        markOwnerWorkReviewed(work.id);
        navigate(OWNER_PATHS.workReviewDone(work.id), { replace: true, state: { rating: form.rating } });
      }}
    />
  );
}

/**
 * 서버 작업의 후기 작성 (ADR 0036). 학생 이름은 결과물(GET /jobs/{id}/result)에서 불러와, 끝나지 않은
 * 작업이면 「아직 끝나지 않은 작업이에요」. 남기면 이 화면을 연 동안 「후기 작성 완료」로 보인다.
 */
function JobReview({ jobId }: { jobId: number }) {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const { load, reload } = useJobResult(jobId);
  const form = useReviewForm();
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 보낸다
  const inFlight = useRef(false);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestRef = useRef(0);

  useEffect(() => {
    const latest = requestRef;
    return () => {
      latest.current += 1;
    };
  }, []);

  if (load.status === "notFound" || load.status === "closed") {
    return <OwnerMissing title="후기 작성" onBack={back} message="아직 끝나지 않은 작업이에요" />;
  }
  if (load.status !== "loaded") {
    return (
      <SubScreen title="후기 작성" onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="작업을 불러오는 중이에요"
          errorText="작업을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }

  const id = String(jobId);
  const studentName = load.data.studentName.trim() || "학생";

  const send = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    const request = ++requestRef.current;
    setSending(true);
    setSendError(null);
    const result = await sendJobReview(jobId, {
      rating: form.rating,
      pointLabels: form.points,
      text: form.text,
    });
    inFlight.current = false;
    if (request !== requestRef.current) return;
    setSending(false);
    switch (result.status) {
      case "done":
        markOwnerWorkReviewed(id);
        navigate(OWNER_PATHS.workReviewDone(id), {
          replace: true,
          state: { rating: form.rating, studentName },
        });
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "forbidden":
        window.alert("내 작업에만 후기를 남길 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "duplicate":
        markOwnerWorkReviewed(id);
        setSendError("이미 후기를 남긴 작업이에요");
        break;
      case "notAvailable":
        setSendError("아직 끝나지 않은 작업이에요");
        break;
      case "invalidInput":
        setSendError("입력한 내용을 다시 확인해 주세요");
        break;
      default:
        setSendError("잠시 후 다시 시도해 주세요");
    }
  };

  return (
    <ReviewScreen
      studentName={studentName}
      form={form}
      onResult={() => navigate(OWNER_PATHS.workResult(id))}
      onSkip={() => navigate(OWNER_PATHS.home, { replace: true })}
      submitLabel={sending ? "보내는 중..." : "후기 남기기"}
      submitDisabled={form.rating === 0 || sending}
      onSubmit={() => void send()}
      error={sendError}
    />
  );
}

export default OwnerReviewPage;
