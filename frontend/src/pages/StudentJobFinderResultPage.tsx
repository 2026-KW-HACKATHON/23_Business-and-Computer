import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Button, CategoryBadge, LoadNotice, SubScreen, TextButton } from "../components";
import {
  JOB_FINDER_BUDGETS,
  JOB_FINDER_PACE,
  STUDENT_PATHS,
  jobFinderResult,
  readJobFinderAnswers,
  useOpenJobs,
} from "../features/student";
import type { JobFinderAnswers, JobFinderPick } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, todayIsoDate } from "../lib/date";
import { formatWon } from "../lib/money";
import { FIELDS } from "../types/field";
import type { Field } from "../types/field";
import "./OwnerTaskFinderPage.css";
import "./StudentJobFinderPage.css";

/** 결과 위에 보이는 내 답 요약. 넘긴 질문은 빠진다 */
function answerSummary({ fields, pace, budget }: JobFinderAnswers): string[] {
  return [
    ...(fields === null ? [] : [fields.length === 0 ? "아무 분야나" : fields.join(" · ")]),
    ...(pace === null ? [] : [JOB_FINDER_PACE[pace].short]),
    ...(budget === null ? [] : [JOB_FINDER_BUDGETS[budget].short]),
  ];
}

/**
 * 피그마 「나에게 맞는 일 찾기 - 결과」 · 「결과 (맞는 의뢰 없음)」 (ADR 0072).
 * GET /explore 의 의뢰를 끝까지 받아 모집 중 · 아직 지원 안 한 의뢰에 점수를 매긴다 (jobFinderResult).
 * 위에 내 답 요약, 「지금 지원하기 좋은 의뢰 N개」(점수 높은 3개) + 「다른 의뢰 N개 더 보기」.
 * 카드마다 맞은 까닭(분야 · 마감 여유 · 작업비 맞음)과 맞지 않는 점(회색 「마감 빠듯」 · 「작업비 범위 밖」)이 보이고,
 * 누르면 의뢰서 전체 보기(거기서 지원하기).
 * 맞는 의뢰가 없으면 「지금 맞는 의뢰가 없어요」와 가게에 먼저 제안하기.
 */
function StudentJobFinderResultPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(STUDENT_PATHS.home);
  const answers = readJobFinderAnswers(location.state);
  const { load, reload } = useOpenJobs();
  const [showMore, setShowMore] = useState(false);

  if (!answers) return <Navigate to={STUDENT_PATHS.jobFinder(1)} replace />;

  const restart = () => navigate(STUDENT_PATHS.jobFinder(1));
  const result = load.status === "loaded" ? jobFinderResult(load.jobs, answers, todayIsoDate()) : undefined;
  const none = result !== undefined && result.picks.length === 0;
  const summary = answerSummary(answers);

  const pickCard = ({ job, matchedFields, roomy, budgetFits, tight, budgetOff }: JobFinderPick) => {
    const field = job.specialtyCategories
      .map((c) => c.name)
      .find((name): name is Field => (FIELDS as string[]).includes(name));
    const reasons = [
      ...matchedFields.map((name) => `${name} 분야`),
      ...(roomy ? ["마감 여유"] : []),
      ...(budgetFits ? ["작업비 맞음"] : []),
    ];
    // 답과 맞지 않는 점. 순서만 뒤로 밀리고 빠지지는 않아서 회색으로 알려 준다
    const misses = [...(tight ? ["마감 빠듯"] : []), ...(budgetOff ? ["작업비 범위 밖"] : [])];
    return (
      <li key={job.jobId}>
        <button
          type="button"
          className="task-finder__pick"
          onClick={() => navigate(STUDENT_PATHS.requestFull(String(job.jobId)))}
        >
          {field && <CategoryBadge field={field} />}
          <strong className="task-finder__pick-title">{job.title}</strong>
          <span className="task-finder__pick-reason">
            {job.storeName} · 초안 마감 {formatMonthDay(job.draftDeadline)}
          </span>
          {reasons.length + misses.length > 0 && (
            <span className="job-finder__reasons">
              {reasons.map((reason) => (
                <span key={reason} className="job-finder__reason">
                  {reason}
                </span>
              ))}
              {misses.map((miss) => (
                <span key={miss} className="job-finder__reason job-finder__reason--miss">
                  {miss}
                </span>
              ))}
            </span>
          )}
          <span className="task-finder__pick-foot">
            <span>{job.budget ? `작업비 ${formatWon(job.budget)}` : "작업비는 의뢰서에서"}</span>
            <span className="task-finder__pick-link">의뢰서 보기 ›</span>
          </span>
        </button>
      </li>
    );
  };

  return (
    <SubScreen
      title="나에게 맞는 일 찾기"
      onBack={back}
      footer={
        <div className="task-finder__footer">
          <Button variant="secondary" fullWidth onClick={restart}>
            다시 찾기
          </Button>
          {none ? (
            <Button tone="student" fullWidth onClick={() => navigate(STUDENT_PATHS.newProposal)}>
              가게에 제안하기
            </Button>
          ) : (
            <Button tone="student" fullWidth onClick={() => navigate(STUDENT_PATHS.home)}>
              홈으로
            </Button>
          )}
        </div>
      }
    >
      <div className="task-finder task-finder--result task-finder--student">
        {summary.length > 0 && (
          <div className="job-finder__summary" aria-label="내 답">
            {summary.map((item) => (
              <span key={item} className="job-finder__summary-item">
                {item}
              </span>
            ))}
          </div>
        )}

        {!result ? (
          <LoadNotice
            status={load.status === "error" ? "error" : "loading"}
            loadingText="지금 모집 중인 의뢰를 찾는 중이에요"
            errorText="의뢰를 불러오지 못했어요"
            onRetry={reload}
          />
        ) : none ? (
          <div className="task-finder__intro">
            <h2 className="task-finder__question">지금 맞는 의뢰가 없어요</h2>
            <p className="task-finder__hint">
              의뢰를 기다리는 대신 자주 가는 가게에 먼저 제안해 보세요. 조건을 바꿔 다시 찾을 수도 있어요
            </p>
          </div>
        ) : (
          <>
            <div className="task-finder__intro">
              <h2 className="task-finder__question">지금 지원하기 좋은 의뢰 {result.picks.length}개</h2>
              <p className="task-finder__hint">답해 주신 내용에 잘 맞는 순서예요. 누르면 의뢰서를 보고 지원할 수 있어요</p>
            </div>
            <ul className="task-finder__picks">{result.picks.map(pickCard)}</ul>
            {result.more.length > 0 && (
              <>
                <TextButton
                  className="task-finder__more"
                  showChevron={!showMore}
                  aria-expanded={showMore}
                  onClick={() => setShowMore((v) => !v)}
                >
                  {showMore ? "다른 의뢰 접기" : `다른 의뢰 ${result.more.length}개 더 보기`}
                </TextButton>
                {showMore && <ul className="task-finder__picks">{result.more.map(pickCard)}</ul>}
              </>
            )}
          </>
        )}
      </div>
    </SubScreen>
  );
}

export default StudentJobFinderResultPage;
