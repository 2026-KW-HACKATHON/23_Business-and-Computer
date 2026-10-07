/* 채팅 (사장님 · 학생 공용). 작업 하나에 채팅방 하나 */

export { default as ChatWorkCard } from "./components/ChatWorkCard";
export type { ChatWorkTroubleItem } from "./components/ChatWorkCard";
export { default as ChatWorkHistory } from "./components/ChatWorkHistory";
export type { ChatWorkHistoryRow } from "./components/ChatWorkHistory";

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
} from "./lib/chatRoom";
export {
  chatWorkBadge,
  chatWorkDocLabel,
  chatWorkDocs,
  chatWorkFlowIndex,
  chatWorkStageOf,
  chatWorkStatusText,
} from "./lib/workDocs";
export type { ChatWorkDoc, ChatWorkProgress, ChatWorkStage } from "./lib/workDocs";
export { attachmentDetailText, isAttachmentExpired } from "./lib/messages";

export type { ChatJobStatus } from "./api/chatApi";
export type { ChatMessage, ChatRoom } from "./types";
