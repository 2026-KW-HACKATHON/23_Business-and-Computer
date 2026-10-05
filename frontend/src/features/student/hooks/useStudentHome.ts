import {
  usePeerProposals,
  useProposalExamples,
  useStudentApplications,
  useStudentWorks,
} from "./useStudentData";
import { useSentProposals } from "./useSentProposals";
import { SAMPLE_REQUESTS } from "../lib/sampleRequests";
import { currentDeadline } from "../lib/format";
import type { StudentHome, StudentTodo, StudentWaitingItem } from "../types";

/**
 * 학생 홈 한 화면 분량. 작업 · 제안 · 지원에서 만든다.
 * 확인할 일 = 동의를 기다리는 의뢰서 · 초안 · 수정안 차례 (마감이 빠른 것부터).
 * 보낸 제안만 API(GET /me/proposals)이고, 나머지는 아직 샘플 데이터다.
 */
export function useStudentHome(): StudentHome {
  const works = useStudentWorks();
  const { load: proposalsLoad, reload: reloadSentProposals } = useSentProposals();
  const proposals = proposalsLoad.status === "loaded" ? proposalsLoad.proposals : [];
  const applications = useStudentApplications();
  const peers = usePeerProposals();
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
    peerProposals: peers
      .filter((p) => p.progress === "waitingAcceptance" && !p.mine)
      .sort((a, b) => b.empathyCount - a.empathyCount),
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
