import { ApiError } from "../../../api/client";
import type { ChatMessageResponse } from "../api/chatApi";
import type { ChatFailure, ChatMessage } from "../types";

/**
 * 보낼 글의 clientMessageId (UUID v4). crypto.randomUUID 는 https · localhost 에서만 있어서,
 * 없으면(http://192.168… 등) crypto.getRandomValues 로 같은 모양을 만든다
 */
export function newClientMessageId(): string {
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // 버전 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // RFC 4122 변형
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** 열람 주소가 이만큼 안에 만료되면 새 주소로 바꾼다 */
const URL_REFRESH_MARGIN_MS = 60_000;

/**
 * 서버 메시지 → 화면 메시지. 내 메시지는 senderUserId 가 viewerUserId 와 같은 것.
 * viewerUserId 를 모르면 knownMine(이 화면에서 보낸 글)일 때만 내 메시지
 */
export function toChatMessage(
  response: ChatMessageResponse,
  viewerUserId: string | undefined,
  knownMine: boolean,
): ChatMessage {
  return {
    id: response.id,
    clientMessageId: response.clientMessageId,
    type: response.type,
    content: response.content ?? undefined,
    attachmentName: response.attachmentName ?? undefined,
    contentExpiresAt: response.contentExpiresAt ?? undefined,
    createdAt: response.createdAt,
    mine: viewerUserId !== undefined ? response.senderUserId === viewerUserId : knownMine,
    status: "sent",
  };
}

/** 사진 · 파일 열람 주소가 만료됐는지 */
export function isAttachmentExpired(message: ChatMessage, now = Date.now()): boolean {
  if (!message.contentExpiresAt) return false;
  return new Date(message.contentExpiresAt).getTime() <= now;
}

const expiresSoon = (message: ChatMessage, now: number) =>
  !message.contentExpiresAt ||
  new Date(message.contentExpiresAt).getTime() - now <= URL_REFRESH_MARGIN_MS;

/**
 * 새로 받은 대화 내역을 지금 목록에 합친다.
 * - 아직 쓸 수 있는 열람 주소는 그대로 둔다 (사진을 매번 다시 받지 않게)
 * - 받은 내역보다 뒤에 저장된 메시지(방금 보낸 글)는 남긴다
 */
export function mergeMessages(
  current: ChatMessage[],
  incoming: ChatMessageResponse[],
  viewerUserId: string | undefined,
  mineIds: ReadonlySet<string>,
  now = Date.now(),
): ChatMessage[] {
  const byId = new Map(current.map((message) => [message.id, message]));
  const merged = incoming.map((response) => {
    const next = toChatMessage(response, viewerUserId, mineIds.has(response.clientMessageId));
    const before = byId.get(response.id);
    if (before?.content && before.content !== next.content && !expiresSoon(before, now)) {
      return { ...next, content: before.content, contentExpiresAt: before.contentExpiresAt };
    }
    return next;
  });
  const lastId = merged.length > 0 ? merged[merged.length - 1].id ?? 0 : 0;
  const newer = current.filter((message) => (message.id ?? 0) > lastId);
  return [...merged, ...newer];
}

/** 메시지 하나를 목록에 넣거나 바꾼다 (id 순서 유지) */
export function upsertMessage(current: ChatMessage[], message: ChatMessage): ChatMessage[] {
  const rest = current.filter((m) => m.id !== message.id);
  return [...rest, message].sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
}

/** API 오류 → 화면이 할 일 */
export function chatFailureOf(error: unknown): ChatFailure {
  if (error instanceof ApiError) {
    if (error.status === 401) return "unauthorized";
    if (error.code === "CHAT_403") return "forbidden";
    if (error.code === "CHAT_ROOM_404") return "notFound";
  }
  return "error";
}

/** 채팅방을 나가며 보이는 안내 */
export const CHAT_LEAVE_MESSAGE: Record<"forbidden" | "notFound", string> = {
  forbidden: "이 채팅방에는 들어갈 수 없어요",
  notFound: "채팅방을 찾을 수 없어요",
};
