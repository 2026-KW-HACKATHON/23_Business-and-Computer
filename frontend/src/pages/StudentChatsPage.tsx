import { useNavigate } from "react-router-dom";
import { ChatRow, LoadNotice } from "../components";
import { chatLastMessageText, chatListStatusText, useChatRooms } from "../features/chat";
import { STUDENT_PATHS, StudentTabScreen } from "../features/student";
import { formatChatTime } from "../lib/date";
import "./StudentChatsPage.css";

/** 피그마 「채팅 목록 (학생)」. 작업 하나에 채팅방 하나 (GET /me/chat-rooms) */
function StudentChatsPage() {
  const navigate = useNavigate();
  const { load, reload } = useChatRooms();

  return (
    <StudentTabScreen tab="chat" title="채팅">
      <p className="student-chats__notice">
        <span aria-hidden="true">ⓘ</span>
        의뢰에 선정되거나, 수락된 제안에 동의해 작업을 시작하면 채팅방이 열려요
      </p>
      {load.status !== "loaded" ? (
        <LoadNotice
          status={load.status}
          loadingText="채팅 목록을 불러오는 중이에요"
          errorText="채팅 목록을 불러오지 못했어요"
          onRetry={reload}
        />
      ) : load.rooms.length > 0 ? (
        <ul className="student-chats__list">
          {load.rooms.map((room) => (
            <li key={room.roomId}>
              <ChatRow
                tone="student"
                partnerRole="owner"
                partnerPhoto={room.counterpartProfileImageUrl}
                name={`${room.counterpartName} 사장님`}
                workTitle={room.jobTitle}
                status={chatListStatusText(room, "student")}
                lastMessage={chatLastMessageText(room)}
                time={room.lastMessage ? formatChatTime(room.lastMessage.createdAt) : ""}
                unreadCount={room.unreadCount}
                onClick={() => navigate(STUDENT_PATHS.chat(room.roomId))}
              />
            </li>
          ))}
        </ul>
      ) : (
        <p className="student-chats__empty">아직 열린 채팅방이 없어요</p>
      )}
    </StudentTabScreen>
  );
}

export default StudentChatsPage;
