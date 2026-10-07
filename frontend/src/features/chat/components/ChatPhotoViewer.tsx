import { PhotoViewer } from "../../../components";
import type { ChatMessage } from "../types";

/** 크게 볼 수 있는 사진: 보낸 사진 중 열람 주소가 있는 것 */
const isViewablePhoto = (message: ChatMessage) =>
  message.type === "IMAGE" && message.status === "sent" && Boolean(message.content);

interface ChatPhotoViewerProps {
  messages: ChatMessage[];
  /** 크게 보는 사진 메시지의 clientMessageId. null 이면 닫혀 있다 */
  openKey: string | null;
  /** 다른 사진으로 넘길 때 (만료된 주소면 새로 받는다) */
  onShow: (message: ChatMessage) => void;
  onClose: () => void;
}

/**
 * 채팅방 사진 크게 보기 (ADR 0050). 참고 사진과 같은 `PhotoViewer` 로 앱 화면 폭 안을 덮고, 그 방의 사진을
 * 보낸 순서대로 ‹ › 로 넘긴다. 위에는 파일 이름(없으면 「사진」)과 n / 전체
 */
function ChatPhotoViewer({ messages, openKey, onShow, onClose }: ChatPhotoViewerProps) {
  const photos = messages.filter(isViewablePhoto);
  const index = photos.findIndex((message) => message.clientMessageId === openKey);
  if (index < 0) return null;
  return (
    <PhotoViewer
      photos={photos.map((message) => ({ url: message.content ?? "", name: message.attachmentName ?? "사진" }))}
      index={index}
      onIndex={(i) => onShow(photos[i])}
      onClose={onClose}
    />
  );
}

export default ChatPhotoViewer;
