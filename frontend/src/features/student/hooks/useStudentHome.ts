import { usePopularProposals } from "../../explore";
import { useProposalExamples, useStudentApplications, useStudentWorks } from "./useStudentData";
import { useSentProposals } from "./useSentProposals";
import { SAMPLE_REQUESTS } from "../lib/sampleRequests";
import { currentDeadline } from "../lib/format";
import type { StudentHome, StudentTodo, StudentWaitingItem } from "../types";

/** 공감 많은 제안을 이만큼 받아 내 제안을 빼고 */
const POPULAR_SIZE = 5;
/** 홈에는 이만큼 보인다 */
const PEER_COUNT = 2;

/**
 * 학생 홈 한 화면 분량. 작업 · 제안 · 지원에서 만든다.
 * 확인할 일 = 동의를 기다리는 의뢰서 · 초안 · 수정안 차례 (마감이 빠른 것부터).
 * 보낸 제안(GET /me/proposals)과 다른 학생 제안(GET /explore 공감 많은 순)은 API 이고,
 * 나머지는 아직 샘플 데이터다. 다른 학생 제안은 둘 다 불러온 뒤에만 채운다 (내 제안을 빼야 해서).
 */
export function useStudentHome(): StudentHome {
  const works = useStudentWorks();
  const { load: proposalsLoad, reload: reloadSentProposals } = useSentProposals();
  const proposals = proposalsLoad.status === "loaded" ? proposalsLoad.proposals : [];
  const applications = useStudentApplications();
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
    ...applications
      .filter((a) => a.status === "reviewing")
      .flatMap((application) => {
        const request = SAMPLE_REQUESTS.find((r) => r.id === application.requestId);
        return request ? [{ type: "application" as const, application, request }] : [];
      }),
  ];

  return {
    firstVisit:
      works.length > 0 || applications.length > 0
        ? false
        : proposalsLoad.status === "loaded"
          ? proposals.length === 0
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
