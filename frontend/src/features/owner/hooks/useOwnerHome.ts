import { SAMPLE_FIRST_VISIT, SAMPLE_REQUEST_EXAMPLES } from "../lib/sampleHome";
import type { OwnerHome, OwnerTodo } from "../types";
import { proposalBadgeNames } from "../../proposal";
import { useOwnerRequests, useOwnerWorks } from "./useOwnerData";
import { useReceivedProposals } from "./useReceivedProposals";

/**
 * 사장님 홈에 그릴 데이터. 작업 · 의뢰 · 제안에서 만들어서, 홈 카드를 눌러 들어간
 * 상세와 내용이 같다. 백엔드를 연동할 때 홈 API 로 바꿔도 화면은 그대로 쓴다.
 * 받은 제안만 API(GET /me/received-proposals, ADR 0025)이고, 나머지는 아직 샘플 데이터다.
 */
export function useOwnerHome(): OwnerHome {
  const works = useOwnerWorks();
  const requests = [...useOwnerRequests()].sort((a, b) => a.draftDue.localeCompare(b.draftDue));
  const { load: proposalsLoad, reload: reloadReceivedProposals } = useReceivedProposals();
  const proposals = proposalsLoad.status === "loaded" ? proposalsLoad.proposals : [];

  // 확인할 일: 도착한 결과물 → 새 제안 → 지원자가 생긴 의뢰
  const todos: OwnerTodo[] = [
    ...works
      .filter((w) => w.status === "submitted")
      .map((w): OwnerTodo => ({
        type: "draftArrived",
        id: w.id,
        kind: w.kind,
        title: w.title,
        field: w.field,
        student: w.student,
        autoCompleteOn: w.autoCompleteOn ?? w.finalDue,
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
      .filter((r) => r.applicants.length > 0)
      .map((r): OwnerTodo => ({
        type: "applicants",
        id: r.id,
        kind: "request",
        title: r.title,
        field: r.field,
        budget: r.budget,
        applicantCount: r.applicants.length,
        draftDue: r.draftDue,
      })),
  ];

  return {
    firstVisit: SAMPLE_FIRST_VISIT,
    todos,
    receivedProposals: proposalsLoad.status,
    reloadReceivedProposals,
    working: works
      .filter((w) => w.status === "inProgress")
      .map((w) => ({
        id: w.id,
        kind: w.kind,
        title: w.title,
        student: w.student,
        stage: w.revisionCount > 0 ? "final" : "draft",
        due: w.revisionCount > 0 ? w.finalDue : w.draftDue,
      })),
    waiting: requests
      .filter((r) => r.applicants.length === 0)
      .map((r) => ({
        id: r.id,
        kind: "request",
        title: r.title,
        stage: "draft",
        due: r.draftDue,
        status: "recruiting",
      })),
    examples: SAMPLE_REQUEST_EXAMPLES,
    done: works
      .filter((w) => w.status === "completed" && w.completedOn)
      .sort((a, b) => (b.completedOn ?? "").localeCompare(a.completedOn ?? ""))
      .map((w) => ({
        id: w.id,
        kind: w.kind,
        title: w.title,
        student: w.student,
        completedOn: w.completedOn ?? "",
      })),
  };
}
