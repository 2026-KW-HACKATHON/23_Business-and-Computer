import { apiData } from "../../../api/client";

/** 메시지 종류 (ChatMessageType) */
export type ChatMessageType = "TEXT" | "IMAGE" | "FILE";

/** 지금 지켜야 할 마감 (DeadlineType). 매칭된 작업만 있다 */
export type ChatDeadlineType = "DRAFT" | "FINAL";

/** 마지막 결과물의 검토 상태 (JobSubmissionReviewStatus) */
export type ChatSubmissionReviewStatus = "PENDING" | "APPROVED" | "REVISION_REQUESTED";

/** 작업 상태 (JobStatus) */
export type ChatJobStatus = "OPEN" | "AWAITING_START" | "MATCHED" | "CLOSED" | "CANCELLED";

/** 채팅방 목록의 마지막 메시지. IMAGE 는 「사진」, FILE 은 파일 이름 */
export interface ChatLastMessageResponse {
  type: ChatMessageType;
  preview: string;
  /** "2026-10-06T14:22:00" (한국 시각) */
  createdAt: string;
}

/** GET /me/chat-rooms 의 방 하나 · GET /chat-rooms/{roomId} (ChatRoomListResponse.Room) */
export interface ChatRoomResponse {
  roomId: string;
  jobId: number;
  jobTitle: string;
  /** 학생에게는 가게 이름, 사장님에게는 학생 이름 */
  counterpartName: string;
  counterpartProfileImageUrl?: string | null;
  /** 작업 상태. 서버가 주지 않으면 없다 */
  jobStatus?: ChatJobStatus | null;
  deadlineType?: ChatDeadlineType | null;
  /** "2026-10-09" */
  deadlineDate?: string | null;
  submissionReviewStatus?: ChatSubmissionReviewStatus | null;
  /** 마지막 결과물이 초안인지 수정안인지. 낸 게 없으면 없다 */
  submissionType?: "DRAFT" | "REVISION" | null;
  /** 마지막 결과물의 수정 번호 (초안 0). 낸 게 없으면 없다 */
  revisionNumber?: number | null;
  /** 제안으로 시작한 작업이면 그 제안 id */
  proposalId?: number | null;
  budget: number;
  revisionCount: number;
  draftDeadline: string;
  finalDeadline: string;
  /** 선정된 지원서. 제안으로 시작한 작업은 없다 */
  applicationSummary?: string | null;
  applicationWorkPlan?: string | null;
  applicationDeliveryMethod?: string | null;
  lastMessage?: ChatLastMessageResponse | null;
  unreadCount: number;
}

/** 메시지 하나 (ChatMessageListResponse.Message) */
export interface ChatMessageResponse {
  id: number;
  roomId: string;
  clientMessageId: string;
  /** 보낸 사람 사용자 ID (users.user_id, 26자 ULID 문자열) */
  senderUserId: string;
  type: ChatMessageType;
  /** TEXT 는 본문, IMAGE · FILE 은 짧게 쓰는 열람 주소 */
  content?: string | null;
  attachmentName?: string | null;
  /** IMAGE · FILE 의 크기 (바이트). TEXT 와 크기를 기록하기 전의 첨부는 없다 */
  attachmentSize?: number | null;
  /** IMAGE · FILE 열람 주소의 만료 시각. TEXT 는 없다 */
  contentExpiresAt?: string | null;
  createdAt: string;
}

/** GET /me/chat-rooms — 내 채팅방 목록. 최근 메시지 순 */
export async function fetchChatRooms(): Promise<ChatRoomResponse[]> {
  const data = await apiData<{ rooms?: ChatRoomResponse[] } | undefined>("/me/chat-rooms");
  return data?.rooms ?? [];
}

/** GET /chat-rooms/{roomId} — 채팅방 하나 */
export async function fetchChatRoom(roomId: string): Promise<ChatRoomResponse> {
  const data = await apiData<ChatRoomResponse | undefined>(`/chat-rooms/${encodeURIComponent(roomId)}`);
  if (!data) throw new Error("Chat room response has no data");
  return data;
}

/** GET /chat-rooms/{roomId}/messages 의 답 */
export interface ChatMessagesResponse {
  /** 지금 로그인한 사용자 ID. senderUserId 와 같으면 내 메시지. 서버가 주지 않으면 없다 */
  viewerUserId?: string | null;
  messages?: ChatMessageResponse[];
}

/** GET /chat-rooms/{roomId}/messages — 대화 내역 전체 (id 오름차순) */
export async function fetchChatMessages(
  roomId: string,
): Promise<{ viewerUserId: string | undefined; messages: ChatMessageResponse[] }> {
  const data = await apiData<ChatMessagesResponse | undefined>(
    `/chat-rooms/${encodeURIComponent(roomId)}/messages`,
  );
  return { viewerUserId: data?.viewerUserId ?? undefined, messages: data?.messages ?? [] };
}

/** GET /chat-rooms/{roomId}/messages/{messageId} — 메시지 하나. 첨부 열람 주소를 새로 받는다 */
export async function fetchChatMessage(roomId: string, messageId: number): Promise<ChatMessageResponse> {
  const data = await apiData<ChatMessageResponse | undefined>(
    `/chat-rooms/${encodeURIComponent(roomId)}/messages/${messageId}`,
  );
  if (!data) throw new Error("Chat message response has no data");
  return data;
}

/** PUT /chat-rooms/{roomId}/read — 이 메시지까지 읽음. 읽은 위치는 앞으로만 옮겨진다 */
export async function markChatRead(roomId: string, lastReadMessageId: number): Promise<void> {
  await apiData<unknown>(`/chat-rooms/${encodeURIComponent(roomId)}/read`, {
    method: "PUT",
    body: JSON.stringify({ lastReadMessageId }),
  });
}

/**
 * POST /chat-rooms/{roomId}/messages — 글 보내기. 같은 clientMessageId 로 다시 보내면
 * 새로 만들지 않고 처음 저장한 메시지를 돌려준다.
 */
export async function sendChatText(
  roomId: string,
  clientMessageId: string,
  content: string,
): Promise<ChatMessageResponse> {
  const data = await apiData<ChatMessageResponse | undefined>(
    `/chat-rooms/${encodeURIComponent(roomId)}/messages`,
    { method: "POST", body: JSON.stringify({ clientMessageId, content }) },
  );
  if (!data) throw new Error("Chat send response has no data");
  return data;
}

/** POST /chat-rooms/{roomId}/attachments/uploads 의 답 (PrepareAttachmentUploadResponse) */
export interface ChatAttachmentUploadResponse {
  uploadId: string;
  /** 저장소에 바로 PUT 하는 주소 (10분) */
  uploadUrl: string;
  /** 업로드 주소 서명에 들어간 헤더 (content-type · x-amz-tagging). PUT 에 그대로 붙인다 */
  uploadHeaders: Record<string, string>;
  uploadUrlExpiresAt: string;
}

/**
 * POST /chat-rooms/{roomId}/attachments/uploads — 사진 · 파일 하나를 올릴 준비. 준비한 업로드는
 * 1시간 안에 메시지로 보내야 한다
 */
export async function prepareChatAttachmentUpload(
  roomId: string,
  request: { type: "IMAGE" | "FILE"; fileName: string; contentType: string; size: number },
): Promise<ChatAttachmentUploadResponse> {
  const data = await apiData<ChatAttachmentUploadResponse | undefined>(
    `/chat-rooms/${encodeURIComponent(roomId)}/attachments/uploads`,
    { method: "POST", body: JSON.stringify(request) },
  );
  if (!data) throw new Error("Chat attachment upload response has no data");
  return data;
}

/**
 * 준비한 주소로 파일을 올린다. 저장소는 백엔드가 아니라서 apiFetch(쿠키 · JSON 헤더)가 아닌
 * fetch 를 직접 쓰고, 서명된 헤더만 그대로 붙인다. 실패하면 던진다
 */
export async function putChatAttachment(upload: ChatAttachmentUploadResponse, file: File): Promise<void> {
  const put = await fetch(upload.uploadUrl, { method: "PUT", headers: upload.uploadHeaders, body: file });
  if (!put.ok) throw new Error(`Chat attachment upload failed (${put.status})`);
}

/**
 * POST /chat-rooms/{roomId}/messages/attachments — 올린 사진 · 파일을 메시지로 보낸다. 같은
 * clientMessageId · uploadId 로 다시 보내면 처음 저장한 메시지를 돌려준다
 */
export async function sendChatAttachment(
  roomId: string,
  request: { clientMessageId: string; type: "IMAGE" | "FILE"; uploadId: string },
): Promise<ChatMessageResponse> {
  const data = await apiData<ChatMessageResponse | undefined>(
    `/chat-rooms/${encodeURIComponent(roomId)}/messages/attachments`,
    { method: "POST", body: JSON.stringify(request) },
  );
  if (!data) throw new Error("Chat attachment send response has no data");
  return data;
}
