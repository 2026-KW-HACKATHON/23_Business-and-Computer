import { useNavigate } from "react-router-dom";
import { ChatRow } from "../components";
import {
  OWNER_PATHS,
  OwnerTabScreen,
  chatProgressText,
  useOwnerChats,
} from "../features/owner";
import { formatChatTime } from "../lib/date";
import "./OwnerChatsPage.css";

/** 피그마 「채팅 목록 (사장님)」. 작업 하나에 채팅방 하나 */
function OwnerChatsPage() {
  const navigate = useNavigate();
  const rooms = useOwnerChats();

  return (
    <OwnerTabScreen tab="chat" title="채팅">
      <p className="owner-chats__notice">
        <span aria-hidden="true">ⓘ</span>
        의뢰는 학생을 골라 결제하면, 제안은 학생이 작업을 시작하면 채팅방이 열려요
      </p>
      {rooms.length > 0 ? (
        <ul className="owner-chats__list">
          {rooms.map((room) => (
            <li key={room.workId}>
              <ChatRow
                tone="owner"
                partnerRole="student"
                name={`${room.student.name} 학생`}
                workTitle={room.workTitle}
                status={chatProgressText(room.progress)}
                lastMessage={room.lastMessage}
                time={formatChatTime(room.lastMessageAt)}
                unreadCount={room.unreadCount}
                onClick={() => navigate(OWNER_PATHS.chat(room.workId))}
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
