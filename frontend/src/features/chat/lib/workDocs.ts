import { formatMonthDay } from "../../../lib/date";
import type { Role } from "../../../types/role";
import type { WorkKind } from "../../../types/workKind";
import type { ChatRoom } from "../types";
import { chatProgressOf } from "./chatRoom";

/**
 * 채팅방 작업의 단계. 도착한 결과물이 초안인지 수정안인지는 채팅방에 없어서 진행 중 목록
 * (GET /me/jobs?status=MATCHED)의 단계로 정한다. 목록이 아직 없으면 채팅방으로 어림한다 (도착한 결과물은 초안으로)
 */
export type ChatWorkStage =
  | "drafting"
  | "draftArrived"
  | "revising"
  | "revisionArrived"
  | "completed"
  | "notConcluded";

/** 진행 중 목록에서 읽은 단계. 사장님 · 학생 목록 모두 이 모양을 준다 */
export interface ChatWorkProgress {
  stage: "drafting" | "revising" | "submitted";
  /** 도착한 · 낸 결과물이 수정안이면 true */
  revisionSubmitted: boolean;
}

export function chatWorkStageOf(room: ChatRoom, progress?: ChatWorkProgress): ChatWorkStage | undefined {
  if (room.jobStatus === "CLOSED") return "completed";
  if (room.jobStatus === "CANCELLED") return "notConcluded";
  if (progress) {
    if (progress.stage === "submitted") return progress.revisionSubmitted ? "revisionArrived" : "draftArrived";
    return progress.stage;
  }
  const now = chatProgressOf(room);
  if (now?.type === "making") return now.stage === "초안" ? "drafting" : "revising";
  if (now?.type === "submitted") return "draftArrived";
  return undefined;
}

/** 작업에 쌓이는 서류. start 는 의뢰서(의뢰로 시작) · 제안서(제안으로 시작) */
export type ChatWorkDoc = "start" | "draft" | "revisionRequest" | "revision" | "result" | "review" | "canceled";

/**
 * 그 단계까지 쌓인 서류 (생긴 순서). 수정을 몇 번 했는지, 끝난 작업에 초안 · 수정안이 있었는지는
 * 서류 이력을 주는 API 가 오기 전까지 몰라서 지금 단계로 어림한다. withReview 면 끝난 작업에 후기를 붙인다
 */
export function chatWorkDocs(stage: ChatWorkStage | undefined, withReview = false): ChatWorkDoc[] {
  switch (stage) {
    case "draftArrived":
      return ["start", "draft"];
    case "revising":
      return ["start", "draft", "revisionRequest"];
    case "revisionArrived":
      return ["start", "draft", "revisionRequest", "revision"];
    case "completed":
      return withReview ? ["start", "result", "review"] : ["start", "result"];
    case "notConcluded":
      return ["start", "canceled"];
    default:
      return ["start"];
  }
}

/** 이력 줄 하나. round 는 수정이 두 번 이상일 때 수정 요청 · 수정안의 회차(1부터), past 면 지난 회차라 아직 열 수 없다 */
export interface ChatWorkEntry {
  doc: ChatWorkDoc;
  round?: number;
  past?: boolean;
}

/**
 * 작업 이력 줄. revisions 는 마지막 결과물의 수정 번호(초안 0)로, 알면 수정 요청 · 수정안을 회차마다 한 줄씩
 * 「수정 요청 1」「수정안 1」… 로 펼치고 마지막 회차만 연다. 수정이 한 번뿐이거나 번호를 모르면 한 줄씩
 */
export function chatWorkEntries(
  stage: ChatWorkStage | undefined,
  revisions: number | undefined,
  withReview = false,
): ChatWorkEntry[] {
  const docs = chatWorkDocs(stage, withReview).map((doc): ChatWorkEntry => ({ doc }));
  if (revisions === undefined || (stage !== "revising" && stage !== "revisionArrived")) return docs;
  // 수정 요청 수: 고치는 중이면 낸 수정안보다 하나 많다
  const requests = stage === "revising" ? revisions + 1 : revisions;
  if (requests <= 1) return docs;
  const rounds: ChatWorkEntry[] = [];
  for (let round = 1; round <= requests; round++) {
    const past = round < requests;
    rounds.push({ doc: "revisionRequest", round, past });
    if (past || stage === "revisionArrived") rounds.push({ doc: "revision", round, past });
  }
  return [{ doc: "start" }, { doc: "draft" }, ...rounds];
}

const DOC_LABEL: Record<Exclude<ChatWorkDoc, "start">, string> = {
  draft: "초안",
  revisionRequest: "수정 요청",
  revision: "수정안",
  result: "결과물",
  review: "후기",
  canceled: "취소 내역",
};

/** 이력 줄 이름. 사장님 · 학생이 같은 이름을 쓴다 */
export function chatWorkDocLabel(doc: ChatWorkDoc, kind: WorkKind): string {
  if (doc === "start") return kind === "proposal" ? "제안서" : "의뢰서";
  return DOC_LABEL[doc];
}

/** 회차가 있으면 「수정 요청 2」처럼 번호를 붙인다 */
export function chatWorkEntryLabel(entry: ChatWorkEntry, kind: WorkKind): string {
  const label = chatWorkDocLabel(entry.doc, kind);
  return entry.round === undefined ? label : `${label} ${entry.round}`;
}

/** 작업 카드의 굵은 진행 상태. 모르면 undefined (그 줄을 숨긴다) */
export function chatWorkStatusText(stage: ChatWorkStage | undefined, room: ChatRoom, viewer: Role): string | undefined {
  const arrive = viewer === "owner" ? "도착" : "제출";
  switch (stage) {
    case "drafting":
      return `초안 만드는 중, ${formatMonthDay(room.draftDeadline)}까지 ${arrive}`;
    case "revising":
      return `수정안 만드는 중, ${formatMonthDay(room.finalDeadline)}까지 ${arrive}`;
    case "draftArrived":
      return viewer === "owner" ? "초안이 도착했어요, 확인해 주세요" : "사장님이 초안을 확인하고 있어요";
    case "revisionArrived":
      return viewer === "owner" ? "수정안이 도착했어요, 확인해 주세요" : "사장님이 수정안을 확인하고 있어요";
    case "completed":
      return "완료된 작업이에요";
    case "notConcluded":
      return "성사되지 않은 작업이에요";
    default:
      return undefined;
  }
}

/** 작업 이력 머리의 상태 글자 */
export function chatWorkBadge(stage: ChatWorkStage | undefined): string {
  if (stage === "completed") return "완료";
  if (stage === "notConcluded") return "성사되지 않음";
  return "작업 중";
}

/**
 * 흐름 막대의 지금 단계 (의뢰 · 제안 0, 시작 1, 초안 2, 수정 3, 완료 4). 끝났으면 5 (모두 지남),
 * 성사되지 않았거나 모르면 undefined (막대를 숨긴다)
 */
export function chatWorkFlowIndex(stage: ChatWorkStage | undefined): number | undefined {
  switch (stage) {
    case "drafting":
    case "draftArrived":
      return 2;
    case "revising":
    case "revisionArrived":
      return 3;
    case "completed":
      return 5;
    default:
      return undefined;
  }
}
