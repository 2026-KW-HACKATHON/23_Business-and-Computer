import { useNavigate } from "react-router-dom";
import { ChatRow, LoadNotice } from "../components";
import { chatLastMessageText, chatListStatusText, useChatRooms } from "../features/chat";
import { OWNER_PATHS, OwnerTabScreen } from "../features/owner";
import { formatChatTime } from "../lib/date";
import { studentTitle } from "../lib/korean";
import "./OwnerChatsPage.css";

/** 피그마 「채팅 목록 (사장님)」. 작업 하나에 채팅방 하나 (GET /me/chat-rooms) */
function OwnerChatsPage() {
  const navigate = useNavigate();
  const { load, reload } = useChatRooms();

  return (
    <OwnerTabScreen tab="chat" title="채팅">
      <p className="owner-chats__notice">
        <span aria-hidden="true">ⓘ</span>
        의뢰는 학생을 골라 결제하면, 제안은 학생이 작업을 시작하면 채팅방이 열려요
      </p>
      {load.status !== "loaded" ? (
        <LoadNotice
          status={load.status}
          loadingText="채팅 목록을 불러오는 중이에요"
          errorText="채팅 목록을 불러오지 못했어요"
          onRetry={reload}
        />
      ) : load.rooms.length > 0 ? (
        <ul className="owner-chats__list">
          {load.rooms.map((room) => (
            <li key={room.roomId}>
              <ChatRow
                tone="owner"
                partnerRole="student"
                partnerPhoto={room.counterpartProfileImageUrl}
                name={studentTitle(room.counterpartName)}
                workTitle={room.jobTitle}
                status={chatListStatusText(room, "owner")}
                lastMessage={chatLastMessageText(room)}
                time={room.lastMessage ? formatChatTime(room.lastMessage.createdAt) : ""}
                unreadCount={room.unreadCount}
                onClick={() => navigate(OWNER_PATHS.chat(room.roomId))}
              />
            </li>
          ))}
        </ul>
      ) : (
        <p className="owner-chats__empty">아직 열린 채팅방이 없어요</p>
      )}
    </OwnerTabScreen>
  );
}

export default OwnerChatsPage;
