import {
  Button,
  CategoryBadge,
  DeadlineBadge,
  TextButton,
  WorkKindIcon,
} from "../../../components";
import { formatMonthDay } from "../../../lib/date";
import { formatWon } from "../../../lib/money";
import { categoryNames, jobStatusLabel } from "../../explore";
import type { ExploreJobCard } from "../../explore";
import "./ExploreCards.css";

interface RequestCardProps {
  job: ExploreJobCard;
  /** 제목 · 「의뢰서 전체 보기」: 의뢰서 전체 보기로 */
  onOpen: () => void;
  onApply: () => void;
}

/**
 * 학생 탐색의 의뢰 카드 (GET /explore 의 JOB). 모집 중이면 초안 마감 뱃지와 「지원하기」.
 * 예산은 서버가 budget 을 줄 때만, 「지원했어요」는 applied 가 true 일 때만 보인다.
 */
function RequestCard({ job, onOpen, onApply }: RequestCardProps) {
  const recruiting = job.status === "OPEN";
  const applied = job.applied === true;

  return (
    <article className="student-card">
      <div className="student-card__head">
        <WorkKindIcon kind="request" size={22} />
        <button type="button" className="student-card__title" onClick={onOpen}>
          {job.title}
        </button>
        {recruiting && (
          <DeadlineBadge stage="draft" due={`${formatMonthDay(job.draftDeadline)}까지`} />
        )}
      </div>
      <div className="student-card__meta">
        {categoryNames(job.specialtyCategories).map((name) => (
          <CategoryBadge key={name} field={name} />
        ))}
        <span className="student-card__sub">{job.storeName}</span>
      </div>
      <p className="student-card__status">{jobStatusLabel(job.status)}</p>
      <div className="student-card__divider" />
      <div className="student-card__footer">
        {job.budget != null && (
          <p className="student-card__budget">
            <span>예산</span>
            <strong>{formatWon(job.budget)}</strong>
          </p>
        )}
        <TextButton onClick={onOpen}>의뢰서 전체 보기</TextButton>
      </div>
      {recruiting && (
        <Button
          tone="student"
          size="medium"
          fullWidth
          variant={applied ? "secondary" : "primary"}
          disabled={applied}
          onClick={onApply}
        >
          {applied ? "지원했어요" : "지원하기"}
        </Button>
      )}
    </article>
  );
}

export default RequestCard;
