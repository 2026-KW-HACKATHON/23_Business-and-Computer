import type { ChatMessageType, ChatRoomResponse } from "./api/chatApi";

/** 채팅방 하나 (GET /me/chat-rooms · GET /chat-rooms/{roomId}) */
export type ChatRoom = ChatRoomResponse;

/** 채팅방 화면의 방 (GET /chat-rooms/{roomId}). reviewed = 이 작업에 사장님 후기가 등록됐는지 */
export type ChatRoomEntry = ChatRoom & { reviewed: boolean };

/** 화면에 그리는 메시지 하나. 보내는 중 · 보내지 못한 글도 같은 모양이다 */
export interface ChatMessage {
  /** 서버 id. 아직 저장되지 않은 글은 없다 */
  id: number | undefined;
  /** 보낼 때 만든 UUID. 다시 보내기와 화면 key 에 쓴다 */
  clientMessageId: string;
  type: ChatMessageType;
  /** TEXT 는 본문, IMAGE · FILE 은 열람 주소 (없으면 열 수 없다) */
  content: string | undefined;
  attachmentName: string | undefined;
  /** 열람 주소의 만료 시각 */
  contentExpiresAt: string | undefined;
  /** 시각 (ISO) */
  createdAt: string;
  /** 내 메시지 (senderUserId === viewerUserId, 모르면 이 화면에서 보낸 글) */
  mine: boolean;
  /** sent = 서버에 저장됨, sending = 보내는 중, failed = 보내지 못함 */
  status: "sent" | "sending" | "failed";
  /** 사진 · 파일의 크기 (바이트). 서버가 준 크기, 없으면 이 화면에서 고른 파일의 크기 */
  fileSize?: number;
  /** 보내지 못한 첨부의 까닭 (「보내지 못했어요」 아래 한 줄) */
  failureReason?: string;
  /** 보내는 중 · 보내지 못한 첨부의 원본 (사진 미리보기 · 다시 올리기) */
  file?: File;
}

/** 채팅 목록 · 작업 카드의 진행 상태 */
export type ChatProgress =
  | { type: "making"; stage: "초안" | "수정안"; due: string }
  | { type: "submitted" }
  | { type: "completed" }
  | { type: "notConcluded" };

/** 채팅 API 실패. unauthorized = 다시 로그인, forbidden · notFound = 목록으로, error = 그 밖 */
export type ChatFailure = "unauthorized" | "forbidden" | "notFound" | "error";
