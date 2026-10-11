import type { ExploreJobCard } from "../../explore/api/exploreApi";
import { FIELDS } from "../../../types/field";
import type { Field } from "../../../types/field";

/**
 * 「나에게 맞는 일 찾기」 (피그마 3페이지 「J. 나에게 맞는 일 찾기」, ADR 0072).
 * 사장님 「맡길 일 찾기」(ADR 0068)의 학생판: 질문 3개(할 수 있는 분야 · 일주일에 낼 수 있는 시간 · 원하는 작업비)로
 * 지금 모집 중인 의뢰에 점수를 매겨 「지금 지원하기 좋은 의뢰」를 고른다. 답은 서버에 저장하지 않는다.
 */

/** 질문 순서. 1번은 분야 여러 개, 2 · 3번은 답 하나 */
export const JOB_FINDER_STEPS = 3;

/** 1번 질문: 할 수 있는 분야. 「아무 일이나 좋아요」는 빈 배열 */
export const JOB_FINDER_FIELDS: readonly Field[] = FIELDS;

/** 2번 질문: 일주일에 낼 수 있는 시간 → 초안 마감까지 이만큼 남은 의뢰가 「마감 여유」. short 는 결과 위 요약 */
export const JOB_FINDER_HOURS = [
  { label: "3시간이 안 돼요", short: "일주일 3시간 미만", needDays: 10 },
  { label: "3~8시간쯤 돼요", short: "일주일 3~8시간", needDays: 5 },
  { label: "8시간 넘게 돼요", short: "일주일 8시간 넘게", needDays: 0 },
] as const;

/** 3번 질문: 원하는 작업비 범위(원). max 가 없으면 그 위로 모두. short 는 결과 위 요약 */
export const JOB_FINDER_BUDGETS = [
  { label: "2만 원이면 충분해요", short: "작업비 2만 원 이하", min: 0, max: 20_000 },
  { label: "2만~4만 원이 좋아요", short: "작업비 2만~4만 원", min: 20_000, max: 40_000 },
  { label: "4만 원 넘는 일이 좋아요", short: "작업비 4만 원 넘게", min: 40_000, max: undefined },
] as const;

/** 화면 기록에 들고 다니는 답. 넘긴 질문은 null, 1번의 빈 배열은 「아무 일이나 좋아요」 */
export interface JobFinderAnswers {
  fields: Field[] | null;
  hours: number | null;
  budget: number | null;
}

export const EMPTY_JOB_FINDER_ANSWERS: JobFinderAnswers = { fields: null, hours: null, budget: null };

/** 지금까지 답한 질문 수 (다음에 볼 질문 번호 - 1) */
export function answeredSteps(answers: JobFinderAnswers): number {
  if (answers.fields === null) return 0;
  if (answers.hours === null) return 1;
  if (answers.budget === null) return 2;
  return 3;
}

/** 화면 기록의 답. 모양이 다르면 버린다 */
export function readJobFinderAnswers(state: unknown): JobFinderAnswers | undefined {
  const raw = (state as { jobFinder?: Partial<JobFinderAnswers> } | null)?.jobFinder;
  if (!raw) return undefined;
  const fields = raw.fields;
  const fieldsOk =
    fields === null || (Array.isArray(fields) && fields.every((f) => (FIELDS as string[]).includes(f)));
  const inRange = (v: unknown, n: number) => v === null || (typeof v === "number" && v >= 0 && v < n);
  if (!fieldsOk || !inRange(raw.hours, JOB_FINDER_HOURS.length) || !inRange(raw.budget, JOB_FINDER_BUDGETS.length)) {
    return undefined;
  }
  return { fields: fields ?? null, hours: raw.hours ?? null, budget: raw.budget ?? null };
}

/** 추천 카드 하나와 그 이유 */
export interface JobFinderPick {
  job: ExploreJobCard;
  /** 답과 맞은 분야 이름 (분야를 고르지 않았으면 없음) */
  matchedFields: string[];
  /** 초안 마감까지 남은 날이 낼 수 있는 시간에 넉넉함 */
  roomy: boolean;
  /** 작업비가 원하는 범위 안 */
  budgetFits: boolean;
}

export interface JobFinderResult {
  /** 먼저 크게 보이는 추천 (점수 높은 순 3개까지) */
  picks: JobFinderPick[];
  /** 「다른 의뢰 N개 더 보기」에 접어 두는 나머지 */
  more: JobFinderPick[];
}

/** 먼저 크게 보이는 추천 수 */
const TOP_PICKS = 3;

const DAY_MS = 24 * 60 * 60 * 1000;

/** "2026-10-12" 까지 today 부터 남은 날 */
function daysUntil(isoDate: string, today: string): number {
  return Math.round((Date.parse(isoDate) - Date.parse(today)) / DAY_MS);
}

/**
 * 지원할 수 있는 의뢰(모집 중 · 아직 지원 안 함 · 초안 마감이 오늘이거나 뒤)만 후보로 둔다.
 * 분야를 골랐으면 그 분야가 하나라도 맞는 의뢰만 남기고(분야 3점), 마감 여유 2점, 작업비가 범위 안이면 2점 ·
 * 바로 옆 범위면 1점. 점수 높은 순, 같으면 초안 마감이 빠른 순으로 3개는 picks, 나머지는 more.
 */
export function jobFinderResult(jobs: readonly ExploreJobCard[], answers: JobFinderAnswers, today: string): JobFinderResult {
  const fields = answers.fields ?? [];
  const hours = answers.hours === null ? undefined : JOB_FINDER_HOURS[answers.hours];
  const budget = answers.budget === null ? undefined : JOB_FINDER_BUDGETS[answers.budget];

  const scored = jobs
    .filter((job) => job.status === "OPEN" && !job.applied && daysUntil(job.draftDeadline, today) >= 0)
    .flatMap((job) => {
      const matchedFields = job.specialtyCategories.map((c) => c.name).filter((name) => (fields as string[]).includes(name));
      if (fields.length > 0 && matchedFields.length === 0) return [];
      const roomy = hours !== undefined && daysUntil(job.draftDeadline, today) >= hours.needDays;
      const amount = job.budget ?? undefined;
      const inBand = (b: (typeof JOB_FINDER_BUDGETS)[number]) =>
        amount !== undefined && amount >= b.min && (b.max === undefined || amount <= b.max);
      const budgetFits = budget !== undefined && inBand(budget);
      const nearBand =
        answers.budget !== null &&
        !budgetFits &&
        [answers.budget - 1, answers.budget + 1].some((i) => JOB_FINDER_BUDGETS[i] && inBand(JOB_FINDER_BUDGETS[i]));
      const score = (matchedFields.length > 0 ? 3 : 0) + (roomy ? 2 : 0) + (budgetFits ? 2 : nearBand ? 1 : 0);
      return [{ pick: { job, matchedFields, roomy, budgetFits }, score }];
    })
    .sort((a, b) => b.score - a.score || a.pick.job.draftDeadline.localeCompare(b.pick.job.draftDeadline))
    .map(({ pick }) => pick);

  return { picks: scored.slice(0, TOP_PICKS), more: scored.slice(TOP_PICKS) };
}
