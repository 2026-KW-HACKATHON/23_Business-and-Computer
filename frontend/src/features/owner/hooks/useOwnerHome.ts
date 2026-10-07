import { SAMPLE_FIRST_VISIT, SAMPLE_REQUEST_EXAMPLES } from "../lib/sampleHome";
import type { OwnerHome, OwnerTodo, StudentRef } from "../types";
import { proposalBadgeNames } from "../../proposal";
import { useOwnerClosedJobs } from "./useOwnerClosedJobs";
import { useOpenJobs } from "./useOwnerJobs";
import { useOwnerProgressJobs } from "./useOwnerProgressJobs";
import { jobCategoryNames } from "../lib/ownerJobs";
import { ownerAutoCompleteOn, ownerProgressDeadline } from "../lib/progressJobs";
import type { OwnerProgressJob } from "../lib/progressJobs";
import { useReceivedProposals } from "./useReceivedProposals";

/** 지금 지킬 마감이 빠른 것부터 */
const byDue = (a: OwnerProgressJob, b: OwnerProgressJob) =>
  ownerProgressDeadline(a).due.localeCompare(ownerProgressDeadline(b).due);

/** 홈 카드 · 줄의 학생 (이름을 모르면 「학생」) */
const studentRef = (job: OwnerProgressJob): StudentRef => ({
  name: job.student.name ?? "",
  department: job.student.major,
});

/**
 * 사장님 홈에 그릴 데이터. 작업 · 의뢰 · 제안에서 만들어서, 홈 카드를 눌러 들어간
 * 상세와 내용이 같다. 백엔드를 연동할 때 홈 API 로 바꿔도 화면은 그대로 쓴다.
 * 받은 제안(GET /me/received-proposals, ADR 0025), 모집 중인 의뢰(GET /me/jobs?status=OPEN, ADR 0030),
 * 진행 중 작업(GET /me/jobs?status=MATCHED, ADR 0035), 끝난 일(GET /me/jobs?status=CLOSED, ADR 0036)은 API 다.
 * 끝난 일은 불러오지 못하면 섹션째 숨는다.
 */
export function useOwnerHome(): OwnerHome {
  // 끝난 내 의뢰 (완료한 것만, 끝난 날 최신순)
  const { load: closedLoad } = useOwnerClosedJobs();
  const completed = closedLoad.status === "loaded" ? closedLoad.jobs.filter((job) => job.outcome === "completed") : [];
  // 모집 중인 내 의뢰 (GET /me/jobs?status=OPEN), 초안 마감이 빠른 것부터
  const { load: openLoad } = useOpenJobs();
  const requests = (openLoad.status === "loaded" ? [...openLoad.data] : []).sort((a, b) =>
    a.draftDeadline.localeCompare(b.draftDeadline),
  );
  const { load: proposalsLoad, reload: reloadReceivedProposals } = useReceivedProposals();
  const proposals = proposalsLoad.status === "loaded" ? proposalsLoad.proposals : [];
  const { load: progressLoad, reload: reloadProgress } = useOwnerProgressJobs();
  const progress = progressLoad.status === "loaded" ? [...progressLoad.jobs].sort(byDue) : [];

  // 확인할 일: 도착한 결과물 → 새 제안 → 지원자가 생긴 의뢰
  const todos: OwnerTodo[] = [
    ...progress
      .filter((job) => job.stage === "submitted")
      .map((job): OwnerTodo => ({
        type: "draftArrived",
        id: String(job.jobId),
        kind: job.kind,
        title: job.title,
        field: jobCategoryNames(job.specialtyCategories)[0] ?? "기타",
        student: studentRef(job),
        revision: job.revisionSubmitted,
        autoCompleteOn: ownerAutoCompleteOn(job),
      })),
    // 결정을 기다리는 제안만. 대분류가 여러 개면 첫 번째를 뱃지로
    ...proposals
      .filter((p) => p.status === "PENDING")
      .map((p): OwnerTodo => ({
        type: "proposalArrived",
        id: String(p.proposalId),
        kind: "proposal",
        title: p.title,
        field: proposalBadgeNames(p.specialtyCategories)[0] ?? "기타",
        student: { name: p.student.name, department: p.student.major ?? undefined },
        empathyCount: p.likeCount,
      })),
    ...requests
      .filter((r) => r.applicantCount > 0)
      .map((r): OwnerTodo => ({
        type: "applicants",
        id: String(r.jobId),
        kind: "request",
        title: r.title,
        field: jobCategoryNames(r.specialtyCategories)[0] ?? "기타",
        applicantCount: r.applicantCount,
        draftDue: r.draftDeadline,
      })),
  ];

  return {
    firstVisit: SAMPLE_FIRST_VISIT,
    todos,
    receivedProposals: proposalsLoad.status,
    reloadReceivedProposals,
    progress: progressLoad.status,
    reloadProgress,
    working: progress
      .filter((job) => job.stage !== "submitted")
      .map((job) => {
        const deadline = ownerProgressDeadline(job);
        return {
          id: String(job.jobId),
          kind: job.kind,
          title: job.title,
          student: studentRef(job),
          stage: deadline.stage,
          due: deadline.due,
          proposalId: job.proposalId !== undefined ? String(job.proposalId) : undefined,
        };
      }),
    waiting: requests
      .filter((r) => r.applicantCount === 0)
      .map((r) => ({
        id: String(r.jobId),
        kind: "request",
        title: r.title,
        stage: "draft",
        due: r.draftDeadline,
        status: "recruiting",
      })),
    examples: SAMPLE_REQUEST_EXAMPLES,
    done: completed.map((job) => ({
      id: String(job.jobId),
      kind: job.kind,
      title: job.title,
      student: { name: job.studentName ?? "" },
      completedOn: job.closedOn,
    })),
  };
}
