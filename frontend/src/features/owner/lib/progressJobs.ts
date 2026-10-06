import { ApiError } from "../../../api/client";
import type { FlowStep } from "../../../components";
import type { WorkKind } from "../../../types/workKind";
import type { ApplicationPlan } from "../../../types/workPlan";
import type { JobSpecialtyCategory } from "../api/jobApi";
import { fetchMyChatRooms, fetchOwnerMatchedJobs } from "../api/progressApi";
import type { OwnerMatchedJobResponse } from "../api/progressApi";
import { fetchReceivedProposals } from "../api/receivedProposalApi";
import type { DeadlineStage } from "../types";
import { flowSteps } from "./flow";

/**
 * 진행 중 작업의 단계. drafting = 학생이 초안을 만드는 중, revising = 수정 요청 뒤 수정안을 만드는 중,
 * submitted = 초안 · 수정안이 도착해 사장님 확인을 기다리는 중
 */
export type OwnerProgressStage = "drafting" | "revising" | "submitted";

/** 학생이 맡아 진행 중인 내 의뢰 하나 (GET /me/jobs?status=MATCHED + 채팅방 · 받은 제안) */
export interface OwnerProgressJob {
  jobId: number;
  /** 학생 제안에서 시작했으면 proposal, 내 의뢰에 지원받았으면 request */
  kind: WorkKind;
  /** 제안에서 시작했으면 그 제안 id (상세보기) */
  proposalId?: number;
  title: string;
  specialtyCategories: JobSpecialtyCategory[];
  draftDeadline: string;
  finalDeadline: string;
  stage: OwnerProgressStage;
  /** 도착한(submitted) 것이 수정안이면 true */
  revisionSubmitted: boolean;
  /** 도착한 결과물 id (submitted 일 때만) */
  pendingSubmissionId?: number;
  student: {
    /** 채팅방 · 받은 제안에서 채운다. 못 불러오면 없음 */
    name?: string;
    studentNumber?: string;
    major?: string;
  };
  /** 작업비(원) · 수정 횟수 · 지원서. 채팅방(GET /me/chat-rooms)에서 채우고, 못 불러오면 없음 */
  budget?: number;
  revisionLimit?: number;
  /** 의뢰에 지원해 맡은 작업의 지원서. 제안으로 시작했으면 없음 */
  plan?: ApplicationPlan;
}

/** 검토를 기다리는 결과물이 있으면 도착, 없으면 수정 요청 뒤인지로 단계를 정한다 */
export function ownerProgressStageOf(job: OwnerMatchedJobResponse): OwnerProgressStage {
  if (job.pendingSubmissionId) return "submitted";
  if (job.progressStage === "REVISION") return "revising";
  return "drafting";
}

/** 지금 지켜야 할 마감. 첫 초안이 오기 전에는 초안 마감, 그 뒤에는 최종 마감 */
export function ownerProgressDeadline(job: OwnerProgressJob): { stage: DeadlineStage; due: string } {
  return job.stage === "drafting"
    ? { stage: "draft", due: job.draftDeadline }
    : { stage: "final", due: job.finalDeadline };
}

/** 도착했거나 만드는 중인 것 (초안 · 수정안) */
export function ownerProgressNoun(job: OwnerProgressJob): "초안" | "수정안" {
  if (job.stage === "drafting") return "초안";
  if (job.stage === "revising") return "수정안";
  return job.revisionSubmitted ? "수정안" : "초안";
}

/** 내 활동 · 진행 중 카드의 진행 상태 */
export function ownerProgressStatusText(job: OwnerProgressJob): string {
  const noun = ownerProgressNoun(job);
  return job.stage === "submitted" ? `${noun}이 도착했어요` : `${noun} 제작 중`;
}

/** 흐름 막대 (의뢰 · 제안 → 시작 → 초안 → 수정 → 완료) */
export function ownerProgressFlowSteps(job: OwnerProgressJob, sub?: string): FlowStep[] {
  const first = job.kind === "proposal" ? "제안" : "의뢰";
  return flowSteps(first, ownerProgressNoun(job) === "수정안" ? 3 : 2, sub);
}

export type OwnerProgressJobsResult =
  | { status: "loaded"; jobs: OwnerProgressJob[] }
  | { status: "unauthorized" }
  | { status: "error" };

/** 지원서 세 칸이 모두 있을 때만 */
function applicationPlan(
  summary: string | null | undefined,
  workPlan: string | null | undefined,
  deliveryMethod: string | null | undefined,
): ApplicationPlan | undefined {
  if (!summary || !workPlan || !deliveryMethod) return undefined;
  return { summary, method: workPlan, deliverable: deliveryMethod };
}

/**
 * 학생이 맡아 진행 중인 내 의뢰를 불러온다. 목록에 없는 학생 이름 · 작업비 · 수정 횟수 · 지원서는
 * 채팅방(GET /me/chat-rooms)에서, 제안에서 시작했는지는 받은 제안(GET /me/received-proposals)의
 * jobId 로 채운다. 그 둘은 실패해도 목록은 그대로 보인다 (빈칸은 숨기고 의뢰로 본다).
 */
export async function loadOwnerProgressJobs(): Promise<OwnerProgressJobsResult> {
  let matched: OwnerMatchedJobResponse[];
  try {
    matched = await fetchOwnerMatchedJobs();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "error" };
  }
  if (matched.length === 0) return { status: "loaded", jobs: [] };

  const [rooms, proposals] = await Promise.all([
    fetchMyChatRooms().catch(() => []),
    fetchReceivedProposals().catch(() => []),
  ]);
  const roomByJob = new Map(rooms.map((room) => [room.jobId, room]));
  const proposalByJob = new Map(
    proposals.flatMap((proposal) => (proposal.jobId ? [[proposal.jobId, proposal] as const] : [])),
  );

  return {
    status: "loaded",
    jobs: matched.map((job) => {
      const room = roomByJob.get(job.jobId);
      const proposal = proposalByJob.get(job.jobId);
      const name = room?.counterpartName?.trim() || proposal?.student.name.trim() || undefined;
      return {
        jobId: job.jobId,
        kind: proposal ? "proposal" : "request",
        proposalId: proposal?.proposalId,
        title: job.title,
        specialtyCategories: job.specialtyCategories,
        draftDeadline: job.draftDeadline,
        finalDeadline: job.finalDeadline,
        stage: ownerProgressStageOf(job),
        revisionSubmitted: job.submissionType === "REVISION",
        pendingSubmissionId: job.pendingSubmissionId ?? undefined,
        student: {
          name,
          studentNumber: job.studentNumber ?? undefined,
          major: job.major?.trim() || undefined,
        },
        budget: room?.budget ?? undefined,
        revisionLimit: room?.revisionCount ?? undefined,
        plan: room
          ? applicationPlan(room.applicationSummary, room.applicationWorkPlan, room.applicationDeliveryMethod)
          : undefined,
      };
    }),
  };
}
