/* 채팅 (사장님 · 학생 공용). 작업 하나에 채팅방 하나 */

export { useChatRoom } from "./hooks/useChatRoom";
export type { ChatRoomLoad, ChatRoomState } from "./hooks/useChatRoom";
export { useChatRooms } from "./hooks/useChatRooms";
export type { ChatRoomsLoad } from "./hooks/useChatRooms";
export { useChatUnread } from "./hooks/useChatUnread";
export { useScrollToLatest } from "./hooks/useScrollToLatest";

export {
  canCancelChatWork,
  canReportChatWork,
  chatLastMessageText,
  chatListStatusText,
  chatPlanOf,
  chatSummaryText,
} from "./lib/chatRoom";
export { isAttachmentExpired } from "./lib/messages";

export type { ChatJobStatus } from "./api/chatApi";
export type { ChatMessage, ChatRoom } from "./types";
