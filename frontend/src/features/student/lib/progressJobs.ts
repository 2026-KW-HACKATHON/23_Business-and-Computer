import { ApiError } from "../../../api/client";
import type { FlowStep } from "../../../components";
import type { WorkKind } from "../../../types/workKind";
import { fetchJobDetail } from "../../explore";
import type { ExploreSpecialtyCategory } from "../../explore";
import { fetchMatchedJobs } from "../api/progressApi";
import type { MatchedJobResponse } from "../api/progressApi";
import { fetchMyProposals } from "../api/proposalApi";
import type { DeadlineStage } from "../types";
import { flowSteps } from "./flow";

/**
 * 진행 중 작업의 단계. drafting = 초안 만드는 중, revising = 수정 요청을 받아 수정안 만드는 중,
 * submitted = 초안 · 수정안을 내고 사장님 확인을 기다리는 중
 */
export type ProgressStage = "drafting" | "revising" | "submitted";

/** 나와 매칭된 진행 중 작업 하나 (GET /me/jobs?status=MATCHED + 가게 · 종류) */
export interface ProgressJob {
  jobId: number;
  /** 내 제안에서 시작했으면 proposal, 의뢰에 뽑혔으면 request */
  kind: WorkKind;
  title: string;
  specialtyCategories: ExploreSpecialtyCategory[];
  /** 작업비(원) */
  budget: number;
  draftDeadline: string;
  finalDeadline: string;
  /** 사장님이 정한 수정 횟수 */
  revisionLimit: number;
  stage: ProgressStage;
  /** 마지막으로 낸 것이 수정안이면 true (확인 중일 때 「수정안」) */
  revisionSubmitted: boolean;
  /** 가게 이름 · 주소 (GET /jobs/{id}). 못 불러오면 비운다 (그 줄을 숨긴다) */
  storeName?: string;
  storeAddress?: string;
}

/** 마지막 결과물의 검토 상태로 단계를 정한다. 아무것도 내지 않았으면 초안 차례 */
export function progressStageOf(job: MatchedJobResponse): ProgressStage {
  if (job.reviewStatus === "REVISION_REQUESTED") return "revising";
  if (job.reviewStatus) return "submitted";
  return "drafting";
}

/** 지금 지켜야 할 마감. 초안을 낸 뒤나 수정 요청을 받은 뒤에는 최종 마감 */
export function progressDeadline(job: ProgressJob): { stage: DeadlineStage; due: string } {
  return job.stage === "drafting"
    ? { stage: "draft", due: job.draftDeadline }
    : { stage: "final", due: job.finalDeadline };
}

/** 내 활동 · 진행 중 카드의 진행 상태 */
export function progressStatusText(job: ProgressJob): string {
  switch (job.stage) {
    case "drafting":
      return "초안 제작 중";
    case "revising":
      return "수정 요청이 왔어요";
    case "submitted":
      return `${job.revisionSubmitted ? "수정안" : "초안"} 제출, 사장님 확인 중`;
  }
}

/** 흐름 막대 (의뢰 · 제안 → 시작 → 초안 → 수정 → 완료) */
export function progressFlowSteps(job: ProgressJob, sub?: string): FlowStep[] {
  const first = job.kind === "proposal" ? "제안" : "의뢰";
  if (job.stage === "drafting") return flowSteps(first, 2, sub);
  if (job.stage === "revising") return flowSteps(first, 3, sub);
  return flowSteps(first, job.revisionSubmitted ? 3 : 2, sub);
}

/** 「가게 이름 · 수정 N회」처럼 비지 않은 것만 잇는다 */
export function progressMeta(job: ProgressJob, ...rest: string[]): string {
  return [job.storeName, ...rest].filter(Boolean).join(" · ");
}

export type ProgressJobsResult =
  | { status: "loaded"; jobs: ProgressJob[] }
  | { status: "unauthorized" }
  | { status: "error" };

/**
 * 진행 중 작업을 불러온다. 목록에 없는 가게 이름 · 주소는 의뢰마다 GET /jobs/{id} 로,
 * 제안에서 시작했는지는 보낸 제안(GET /me/proposals)의 jobId 로 채운다. 그 둘은 실패해도
 * 목록은 그대로 보인다 (가게 줄을 숨기고 의뢰로 본다).
 */
export async function loadProgressJobs(): Promise<ProgressJobsResult> {
  let matched: MatchedJobResponse[];
  try {
    matched = await fetchMatchedJobs();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "error" };
  }
  if (matched.length === 0) return { status: "loaded", jobs: [] };

  const [proposals, details] = await Promise.all([
    fetchMyProposals().catch(() => []),
    Promise.all(matched.map((job) => fetchJobDetail(job.jobId).catch(() => undefined))),
  ]);
  const proposalJobIds = new Set(proposals.map((proposal) => proposal.jobId));

  return {
    status: "loaded",
    jobs: matched.map((job, i) => ({
      jobId: job.jobId,
      kind: proposalJobIds.has(job.jobId) ? "proposal" : "request",
      title: job.title,
      specialtyCategories: job.specialtyCategories,
      budget: job.budget,
      draftDeadline: job.draftDeadline,
      finalDeadline: job.finalDeadline,
      revisionLimit: job.revisionCount,
      stage: progressStageOf(job),
      revisionSubmitted: job.submissionType === "REVISION",
      storeName: details[i]?.storeName?.trim() || undefined,
      storeAddress: details[i]?.storeAddress?.trim() || undefined,
    })),
  };
}
