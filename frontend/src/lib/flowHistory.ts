/**
 * 끝나는 흐름(의뢰 등록 · 제안 보내기 · 결제)의 방문 기록 (ADR 0047).
 * 흐름 첫 화면에 들어오면 방문 기록의 위치를 남기고, 흐름을 끝낼 때 그 앞까지 되감은 뒤 도착 화면을 연다.
 * 그래서 끝낸 뒤 뒤로가기로 흐름 화면에 돌아가 같은 일을 다시 끝낼 수 없다.
 * 카카오페이처럼 다른 사이트에 다녀와도 이어지도록 위치와 할 일은 sessionStorage 에 둔다.
 */

/** 흐름마다 그 흐름의 화면 주소. 「:이름」은 한 칸이고, 첫 칸 값(의뢰 · 제안 id)이 같아야 같은 흐름이다 */
const FLOWS: { flow: string; patterns: string[] }[] = [
  {
    flow: "requestNew",
    patterns: ["/owner/requests/new", "/owner/requests/new/2", "/owner/requests/new/3"],
  },
  {
    flow: "ownerPay",
    patterns: [
      "/owner/requests/:requestId/applicants",
      "/owner/requests/:requestId/applicants/:applicationId",
      "/owner/requests/:requestId/assign/:applicationId",
      "/owner/requests/:requestId/assign/:applicationId/pay",
    ],
  },
  {
    flow: "proposalAccept",
    patterns: ["/owner/proposals/:proposalId", "/owner/proposals/:proposalId/accept"],
  },
  {
    flow: "proposalNew",
    patterns: [
      "/student/proposals/new",
      "/student/proposals/new/2",
      "/student/proposals/new/3",
      "/student/proposals/new/4",
    ],
  },
];

/** 흐름을 끝낼 때 쓰는 이름 */
export const FLOW_KEYS = {
  requestNew: "requestNew",
  proposalNew: "proposalNew",
  ownerPay: (requestId: number | string) => `ownerPay:${requestId}`,
  proposalAccept: (proposalId: number | string) => `proposalAccept:${proposalId}`,
};

const START_PREFIX = "gakkum.flowStart:";
/** 브라우저가 세는 방문 기록 칸 수의 한도 (크롬 · 파이어폭스 50). 닿으면 history.length 로 위치를 알 수 없다 */
const HISTORY_LENGTH_CAP = 50;
/** 이 문서(페이지 로드)의 표시. 카카오페이에 다녀오면 문서가 바뀐다 */
const DOCUMENT_ID = Math.random().toString(36).slice(2);
const FINISH_KEY = "gakkum.flowFinish";
/** 되감는 동안만 쓰는 할 일. 그보다 오래된 것은 버린다 */
const FINISH_TTL_MS = 5000;

/** 이 주소가 속한 흐름의 이름. 흐름 화면이 아니면 undefined */
export function flowKeyOf(pathname: string): string | undefined {
  const parts = pathname.replace(/\/+$/, "").split("/");
  for (const { flow, patterns } of FLOWS) {
    for (const pattern of patterns) {
      const expected = pattern.split("/");
      if (expected.length !== parts.length) continue;
      let firstParam: string | undefined;
      const matches = expected.every((segment, i) => {
        if (!segment.startsWith(":")) return segment === parts[i];
        firstParam ??= parts[i];
        return parts[i] !== "";
      });
      if (matches) return firstParam === undefined ? flow : `${flow}:${firstParam}`;
    }
  }
  return undefined;
}

/** 라우터가 방문 기록 칸마다 붙이는 순번 (이 문서 안에서 하나씩 는다). 모르면 null */
function routerIndex(): number | null {
  const idx = (window.history.state as { idx?: unknown } | null)?.idx;
  return typeof idx === "number" ? idx : null;
}

/**
 * 흐름 첫 화면이 열렸을 때의 방문 기록. hasPrevious 는 바로 앞 칸이 이 앱 화면인지.
 * 같은 문서에서 끝내면 라우터 순번(idx)으로, 다른 문서(카카오페이에 다녀옴)에서 끝내면 history.length 로 칸 수를 센다
 */
interface FlowStart {
  documentId: string;
  idx: number | null;
  length: number;
  hasPrevious: boolean;
}

/** 지금 화면을 흐름 첫 화면으로 남긴다 */
export function saveFlowStart(key: string, hasPrevious: boolean): void {
  const start: FlowStart = {
    documentId: DOCUMENT_ID,
    idx: routerIndex(),
    length: window.history.length,
    hasPrevious,
  };
  try {
    sessionStorage.setItem(START_PREFIX + key, JSON.stringify(start));
  } catch {
    // 저장소를 못 쓰면 끝낼 때 지금 화면만 바꾼다
  }
}

function takeFlowStart(key: string): FlowStart | undefined {
  try {
    const raw = sessionStorage.getItem(START_PREFIX + key);
    sessionStorage.removeItem(START_PREFIX + key);
    const value = raw ? (JSON.parse(raw) as Partial<FlowStart>) : undefined;
    if (!value || typeof value.documentId !== "string" || typeof value.hasPrevious !== "boolean") return undefined;
    if (!Number.isSafeInteger(value.length)) return undefined;
    return {
      documentId: value.documentId,
      idx: Number.isSafeInteger(value.idx) ? (value.idx as number) : null,
      length: value.length as number,
      hasPrevious: value.hasPrevious,
    };
  } catch {
    return undefined;
  }
}

/** 흐름 첫 칸에서 지금 칸까지 몇 칸인지. 셀 수 없으면 undefined */
function stepsSince(start: FlowStart): number | undefined {
  const idx = routerIndex();
  if (start.documentId === DOCUMENT_ID && start.idx !== null && idx !== null) return idx - start.idx;
  // 다른 문서: 지금 위치는 history.length - 1 이다 (앞으로 가기 기록은 새 화면을 열 때 지워진다).
  // 브라우저 한도에 닿았으면 칸 수가 더 늘지 않아 셀 수 없다
  if (start.length >= HISTORY_LENGTH_CAP || window.history.length >= HISTORY_LENGTH_CAP) return undefined;
  return window.history.length - start.length;
}

/**
 * 흐름을 끝낼 때 되감을 칸 수와 도착 화면을 여는 방법.
 * 앞 칸이 이 앱 화면이면 그 칸까지 되감고 도착 화면을 새로 연다 (흐름 화면이 앞으로 가기 기록에서도 지워진다).
 * 아니면 흐름 첫 칸까지 되감고 그 칸을 도착 화면으로 바꾼다. 기록이 없거나 셀 수 없으면 되감지 않는다.
 */
export function planFinish(key: string): { back: number; push: boolean } {
  const start = takeFlowStart(key);
  const toStart = start && stepsSince(start);
  if (!start || toStart === undefined || toStart < 0) return { back: 0, push: false };
  return start.hasPrevious ? { back: toStart + 1, push: true } : { back: toStart, push: false };
}

/** 되감은 뒤 열 도착 화면 */
export interface PendingFinish {
  to: string;
  state?: unknown;
  push: boolean;
}

/** 되감기 전에 도착 화면을 남긴다. 남기지 못하면 false (되감지 말고 지금 화면을 바꾼다) */
export function savePendingFinish(pending: PendingFinish): boolean {
  try {
    sessionStorage.setItem(FINISH_KEY, JSON.stringify({ ...pending, expiresAt: Date.now() + FINISH_TTL_MS }));
    return true;
  } catch {
    return false;
  }
}

/** 남겨 둔 도착 화면을 꺼낸다 (한 번만). 오래됐으면 버린다 */
export function takePendingFinish(): PendingFinish | undefined {
  try {
    const raw = sessionStorage.getItem(FINISH_KEY);
    if (!raw) return undefined;
    sessionStorage.removeItem(FINISH_KEY);
    const value = JSON.parse(raw) as Partial<PendingFinish> & { expiresAt?: number };
    if (typeof value.to !== "string" || !value.expiresAt || value.expiresAt < Date.now()) return undefined;
    return { to: value.to, state: value.state, push: value.push === true };
  } catch {
    return undefined;
  }
}
