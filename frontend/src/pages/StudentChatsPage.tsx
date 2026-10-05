import { useNavigate } from "react-router-dom";
import { ChatRow } from "../components";
import { STUDENT_PATHS, StudentTabScreen, useStudentChats } from "../features/student";
import { formatChatTime } from "../lib/date";
import "./StudentChatsPage.css";

/** 피그마 「채팅 목록 (학생)」. 작업 하나에 채팅방 하나 */
function StudentChatsPage() {
  const navigate = useNavigate();
  const rooms = useStudentChats();

  return (
    <StudentTabScreen tab="chat" title="채팅">
      <p className="student-chats__notice">
        <span aria-hidden="true">ⓘ</span>
        의뢰에 선정되거나 제안이 수락되면 그 작업의 채팅방이 열려요
      </p>
      {rooms.length > 0 ? (
        <ul className="student-chats__list">
          {rooms.map((room) => (
            <li key={room.workId}>
              <ChatRow
                tone="student"
                partnerRole="owner"
                name={`${room.storeName} 사장님`}
                workTitle={room.workTitle}
                status={room.status}
                lastMessage={room.lastMessage}
                time={formatChatTime(room.lastMessageAt)}
                unreadCount={room.unreadCount}
                onClick={() => navigate(STUDENT_PATHS.chat(room.workId))}
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
