import { ApiError } from "../../../api/client";
import { fetchOwnerHome } from "../api/homeApi";
import type {
  OwnerHomeDoneResponse,
  OwnerHomeResponse,
  OwnerHomeStudentResponse,
  OwnerHomeTodoResponse,
  OwnerHomeWaitingResponse,
  OwnerHomeWorkingResponse,
} from "../api/homeApi";
import type { OwnerDoneItem, OwnerTodo, OwnerWaitingItem, OwnerWorkingItem, StudentRef } from "../types";

/** 사장님 홈에 그릴 섹션들 (GET /me/home). 섹션이 null 이면 그 섹션만 불러오지 못했다 */
export interface OwnerHomeData {
  /** 서버가 이력이 하나도 없다고 답했을 때만 true. 모르면(null) 일반 홈 */
  firstVisit: boolean;
  todos: OwnerTodo[] | null;
  working: OwnerWorkingItem[] | null;
  waiting: OwnerWaitingItem[] | null;
  done: OwnerDoneItem[] | null;
}

export type OwnerHomeResult =
  | { status: "loaded"; home: OwnerHomeData }
  | { status: "unauthorized" }
  /** 403 HOME_403 — 가입을 끝내지 않았다 */
  | { status: "signupRequired" }
  | { status: "error" };

/** 카드의 학생 (이름을 모르면 「학생」) */
function studentRef(student: OwnerHomeStudentResponse | null | undefined): StudentRef {
  return { name: student?.name?.trim() ?? "", department: student?.major?.trim() || undefined };
}

/** 확인할 일 카드 한 장. 버튼이 갈 곳의 id 가 없거나 모르는 종류면 undefined (카드를 빼고 보인다) */
function toTodo(todo: OwnerHomeTodoResponse): OwnerTodo | undefined {
  const base = { kind: todo.kind, title: todo.title, field: todo.field?.trim() || "기타" };
  switch (todo.type) {
    case "draftArrived":
      if (todo.jobId == null) return undefined;
      return {
        ...base,
        type: "draftArrived",
        id: String(todo.jobId),
        student: studentRef(todo.student),
        revision: todo.revision ?? false,
        autoCompleteOn: todo.autoCompleteOn ?? undefined,
      };
    case "proposalArrived":
      if (todo.proposalId == null) return undefined;
      return {
        ...base,
        type: "proposalArrived",
        id: String(todo.proposalId),
        student: studentRef(todo.student),
        empathyCount: todo.likeCount ?? 0,
      };
    case "applicants":
      if (todo.jobId == null || !todo.draftDeadline) return undefined;
      return {
        ...base,
        type: "applicants",
        id: String(todo.jobId),
        applicantCount: todo.applicantCount ?? 0,
        draftDue: todo.draftDeadline,
      };
    default:
      return undefined;
  }
}

function toWorking(work: OwnerHomeWorkingResponse): OwnerWorkingItem {
  return {
    id: String(work.jobId),
    kind: work.kind,
    title: work.title,
    student: { name: work.studentName?.trim() ?? "" },
    stage: work.stage === "draft" ? "draft" : "final",
    due: work.due,
    proposalId: work.proposalId != null ? String(work.proposalId) : undefined,
  };
}

function toWaiting(item: OwnerHomeWaitingResponse): OwnerWaitingItem {
  return {
    id: String(item.jobId),
    kind: item.kind,
    title: item.title,
    stage: "draft",
    due: item.draftDeadline,
    status: "recruiting",
  };
}

function toDone(item: OwnerHomeDoneResponse): OwnerDoneItem {
  return {
    id: String(item.jobId),
    kind: item.kind,
    title: item.title,
    student: { name: item.studentName?.trim() ?? "" },
    completedOn: item.completedOn,
  };
}

/** 섹션 순서는 서버가 정한 그대로 둔다. null 은 null 그대로 (그 섹션만 다시 시도) */
function toOwnerHomeData(home: OwnerHomeResponse): OwnerHomeData {
  return {
    firstVisit: home.firstVisit === true,
    todos: home.todos?.flatMap((todo) => toTodo(todo) ?? []) ?? null,
    working: home.working?.map(toWorking) ?? null,
    waiting: home.waiting?.map(toWaiting) ?? null,
    done: home.done?.map(toDone) ?? null,
  };
}

/**
 * 사장님 홈을 한 번에 불러온다 (GET /me/home). 401 은 unauthorized, 403 HOME_403 은 signupRequired,
 * 사장님 응답이 아니거나 그 밖의 실패는 error.
 */
export async function loadOwnerHome(): Promise<OwnerHomeResult> {
  try {
    const home = await fetchOwnerHome();
    if (!home) return { status: "error" };
    return { status: "loaded", home: toOwnerHomeData(home) };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    if (error instanceof ApiError && error.code === "HOME_403") return { status: "signupRequired" };
    return { status: "error" };
  }
}
