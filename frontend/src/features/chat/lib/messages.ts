import { ApiError } from "../../../api/client";
import type { ChatMessageResponse } from "../api/chatApi";
import type { ChatFailure, ChatMessage } from "../types";
import {
  ATTACHMENT_MAX_BYTES,
  attachmentFormatOf,
  extensionOf,
  fileSizeText,
} from "../../../lib/attachmentFormats";
import type { AttachmentType } from "../../../lib/attachmentFormats";

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

/** 보내기 전에 확인한 첨부. 안 되면 알림 문구 */
export type AttachmentCheck =
  | { ok: true; type: AttachmentType; contentType: string }
  | { ok: false; message: string };

/** 고른 파일을 보내기 전에 형식 · 크기를 확인한다 (HEIC 사진 · 한글 문서는 받지 않는 형식) */
export function checkAttachment(file: File): AttachmentCheck {
  const format = attachmentFormatOf(file);
  if (!format) {
    return {
      ok: false,
      message:
        "보낼 수 없는 형식이에요.\n사진은 JPG·PNG·WEBP·GIF, 파일은 PDF·ZIP·워드·엑셀·파워포인트만 보낼 수 있어요",
    };
  }
  if (file.size === 0) return { ok: false, message: "빈 파일은 보낼 수 없어요" };
  if (file.size > ATTACHMENT_MAX_BYTES[format.type]) {
    return {
      ok: false,
      message: format.type === "IMAGE" ? "사진은 10MB까지 보낼 수 있어요" : "파일은 50MB까지 보낼 수 있어요",
    };
  }
  return { ok: true, ...format };
}

/**
 * 첨부 보내기 실패를 화면이 할 일로. drop = 알림 뒤 말풍선을 지움 (다시 보내도 안 됨),
 * retry = 「보내지 못했어요 · 다시 보내기」 말풍선. restart 면 다시 보낼 때 새 clientMessageId 로
 * 준비부터 한다. 401 · CHAT_403 · CHAT_ROOM_404 는 chatFailureOf 가 먼저 받는다
 */
export type AttachmentFailure =
  | { action: "drop"; message: string }
  | { action: "retry"; reason: string | undefined; restart: boolean };

export function attachmentFailureOf(error: unknown): AttachmentFailure {
  const code = error instanceof ApiError ? error.code : undefined;
  switch (code) {
    case "CHAT_UPLOAD_400_TYPE":
      return { action: "drop", message: "보낼 수 없는 형식이에요" };
    case "CHAT_UPLOAD_400_SIZE":
      return { action: "drop", message: "사진은 10MB, 파일은 50MB까지 보낼 수 있어요" };
    case "CHAT_UPLOAD_409_USED":
      return { action: "drop", message: "이미 다른 메시지로 보낸 파일이에요" };
    case "CHAT_UPLOAD_404":
      return { action: "retry", reason: "올린 파일을 찾지 못했어요", restart: true };
    case "CHAT_UPLOAD_409_NOT_READY":
      return { action: "retry", reason: "올리기가 끝나지 않았거나 시간이 지났어요", restart: true };
    case "CHAT_MESSAGE_409":
      return { action: "retry", reason: "같은 메시지로 다시 보낼 수 없어요", restart: true };
    case "CHAT_UPLOAD_502":
      return { action: "retry", reason: "파일 저장소에 연결하지 못했어요", restart: false };
    default:
      return { action: "retry", reason: undefined, restart: false };
  }
}

/** 파일 말풍선 아래 줄 「PDF · 2.1MB」. 크기를 모르면 (다른 사람이 보낸 · 예전 첨부) undefined */
export function attachmentDetailText(message: ChatMessage): string | undefined {
  if (message.fileSize === undefined) return undefined;
  const extension = message.attachmentName ? extensionOf(message.attachmentName).toUpperCase() : "";
  return [extension, fileSizeText(message.fileSize)].filter(Boolean).join(" · ");
}
