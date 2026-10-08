import { ApiError } from "../../../api/client";
import { fetchStudentHome } from "../api/homeApi";
import type {
  StudentHomeCheckingResponse,
  StudentHomeDoneResponse,
  StudentHomePeerProposalResponse,
  StudentHomeResponse,
  StudentHomeTodoResponse,
  StudentHomeWaitingResponse,
} from "../api/homeApi";
import type {
  StudentCheckingItem,
  StudentDoneItem,
  StudentHome,
  StudentPeerProposal,
  StudentTodo,
  StudentWaitingItem,
} from "../types";

/** 빈 글자는 없는 것으로 본다 */
function text(value: string | null | undefined): string | undefined {
  return value?.trim() || undefined;
}

/** 열 화면의 id 가 없는 항목은 누를 곳이 없어서 뺀다 */
function toTodo(todo: StudentHomeTodoResponse): StudentTodo | undefined {
  const base = {
    title: todo.title,
    categories: todo.categories ?? [],
    storeName: text(todo.storeName),
  };
  if (todo.type === "proposalAgreement") {
    if (todo.proposalId == null) return undefined;
    return { type: "proposalAgreement", proposalId: todo.proposalId, ...base };
  }
  // 작업 차례에는 서버가 늘 마감을 준다 (의뢰의 초안 · 최종 마감은 비지 않는다)
  const due = text(todo.due);
  if (todo.jobId == null || !due) return undefined;
  return {
    type: todo.type,
    jobId: todo.jobId,
    kind: todo.kind,
    ...base,
    stage: todo.stage ?? (todo.type === "drafting" ? "draft" : "final"),
    due,
  };
}

function toChecking(job: StudentHomeCheckingResponse): StudentCheckingItem {
  return {
    jobId: job.jobId,
    kind: job.kind,
    title: job.title,
    storeName: text(job.storeName),
    revisionSubmitted: job.submissionType === "REVISION",
    submittedOn: text(job.submittedOn),
  };
}

function toWaiting(item: StudentHomeWaitingResponse): StudentWaitingItem | undefined {
  if (item.type === "proposal") {
    if (item.proposalId == null) return undefined;
    return {
      type: "proposal",
      proposalId: item.proposalId,
      title: item.title,
      storeName: text(item.storeName),
      likeCount: item.likeCount ?? 0,
    };
  }
  if (item.jobId == null || item.jobApplicationId == null) return undefined;
  return {
    type: "application",
    jobId: item.jobId,
    jobApplicationId: item.jobApplicationId,
    title: item.title,
    storeName: text(item.storeName),
    draftDeadline: text(item.draftDeadline),
  };
}

function toPeer(proposal: StudentHomePeerProposalResponse): StudentPeerProposal {
  return {
    proposalId: proposal.proposalId,
    title: proposal.title,
    studentName: text(proposal.studentName),
    storeName: text(proposal.storeName) ?? "",
    status: proposal.status ?? undefined,
    likeCount: proposal.likeCount,
    likedByMe: proposal.likedByMe,
  };
}

function toDone(job: StudentHomeDoneResponse): StudentDoneItem {
  return {
    jobId: job.jobId,
    kind: job.kind,
    title: job.title,
    storeName: text(job.storeName),
    completedOn: text(job.completedOn),
  };
}

/** null(그 섹션 실패)은 그대로 두고, 불러온 목록만 화면 모양으로 바꾼다 */
function mapList<From, To>(list: From[] | null | undefined, to: (item: From) => To | undefined): To[] | null {
  if (list == null) return null;
  return list.flatMap((item) => {
    const mapped = to(item);
    return mapped === undefined ? [] : [mapped];
  });
}

/**
 * GET /me/home 의 답을 홈 화면 모양으로. firstVisit 은 서버 값을 따르고, 서버가 모르면(null)
 * 보이는 목록에 항목이 하나라도 있을 때만 처음이 아니라고 본다 (나머지는 모름).
 * 다른 학생 제안은 이력이 아니라서 처음인지 가리는 데 쓰지 않는다.
 */
export function toStudentHome(response: StudentHomeResponse): StudentHome {
  const todos = mapList(response.todos, toTodo);
  const checking = mapList(response.checking, toChecking);
  const waiting = mapList(response.waiting, toWaiting);
  const done = mapList(response.done, toDone);
  const hasHistory = [todos, checking, waiting, done].some((list) => list !== null && list.length > 0);
  return {
    firstVisit: response.firstVisit ?? (hasHistory ? false : undefined),
    todos,
    checking,
    waiting,
    peerProposals: mapList(response.peerProposals, toPeer) ?? [],
    done,
  };
}

export type StudentHomeResult =
  | { status: "loaded"; home: StudentHome }
  | { status: "unauthorized" }
  | { status: "error" };

/** 학생 홈을 한 번에 불러온다. 섹션 하나가 실패해도 나머지는 loaded 로 온다 */
export async function loadStudentHome(): Promise<StudentHomeResult> {
  try {
    return { status: "loaded", home: toStudentHome(await fetchStudentHome()) };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "error" };
  }
}
