import { usePopularProposals } from "../../explore";
import { useAppliedJobs } from "./useAppliedJobs";
import { useProposalExamples, useStudentWorks } from "./useStudentData";
import { useSentProposals } from "./useSentProposals";
import { currentDeadline } from "../lib/format";
import type { StudentHome, StudentTodo, StudentWaitingItem } from "../types";

/** 공감 많은 제안을 이만큼 받아 내 제안을 빼고 */
const POPULAR_SIZE = 5;
/** 홈에는 이만큼 보인다 */
const PEER_COUNT = 2;

/**
 * 학생 홈 한 화면 분량. 작업 · 제안 · 지원에서 만든다.
 * 확인할 일 = 동의를 기다리는 의뢰서 · 초안 · 수정안 차례 (마감이 빠른 것부터).
 * 보낸 제안(GET /me/proposals) · 지원한 의뢰(GET /me/job-applications) · 다른 학생 제안
 * (GET /explore 공감 많은 순)은 API 이고, 작업은 아직 샘플 데이터다. 다른 학생 제안은 내 제안 목록과
 * 둘 다 불러온 뒤에만 채운다 (내 제안을 빼야 해서).
 */
export function useStudentHome(): StudentHome {
  const works = useStudentWorks();
  const { load: proposalsLoad, reload: reloadSentProposals } = useSentProposals();
  const proposals = proposalsLoad.status === "loaded" ? proposalsLoad.proposals : [];
  const { load: appliedLoad } = useAppliedJobs();
  const applied = appliedLoad.status === "loaded" ? appliedLoad.jobs : [];
  const popular = usePopularProposals(POPULAR_SIZE);
  const examples = useProposalExamples();

  const todos: StudentTodo[] = works
    .flatMap((work): StudentTodo[] => {
      if (work.status === "awaitingAgreement") return [{ type: "agreement", work }];
      if (work.status === "drafting") return [{ type: "drafting", work }];
      if (work.status === "revising") return [{ type: "revising", work }];
      return [];
    })
    .sort((a, b) => currentDeadline(a.work).due.localeCompare(currentDeadline(b.work).due));

  const waiting: StudentWaitingItem[] = [
    ...proposals
      .filter((p) => p.status === "PENDING")
      .map((proposal) => ({ type: "proposal" as const, proposal })),
    ...applied
      .filter((job) => job.applicationStatus === "PENDING")
      .map((job) => ({ type: "application" as const, job })),
  ];

  return {
    firstVisit:
      works.length > 0 || applied.length > 0 || proposals.length > 0
        ? false
        : proposalsLoad.status === "loaded" && appliedLoad.status === "loaded"
          ? true
          : undefined,
    todos,
    peerProposals:
      popular.status === "loaded" && proposalsLoad.status === "loaded"
        ? popular.proposals
            .filter((peer) => !proposals.some((mine) => mine.proposalId === peer.proposalId))
            .slice(0, PEER_COUNT)
        : [],
    checking: works.filter((w) => w.status === "submitted"),
    waiting,
    sentProposals: proposalsLoad.status,
    reloadSentProposals,
    examples,
    done: works
      .filter((w) => w.status === "completed")
      .sort((a, b) => (b.completedOn ?? "").localeCompare(a.completedOn ?? "")),
  };
}
