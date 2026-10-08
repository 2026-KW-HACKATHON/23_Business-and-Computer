import { useCallback, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { fetchChatRooms } from "../api/chatApi";

/** 그 작업(의뢰)의 채팅방 id (GET /me/chat-rooms 의 jobId 로 찾는다). 없거나 불러오지 못하면 undefined */
export async function findJobChatRoomId(jobId: number): Promise<string | undefined> {
  try {
    const rooms = await fetchChatRooms();
    return rooms.find((room) => room.jobId === jobId)?.roomId;
  } catch {
    return undefined;
  }
}

/**
 * 「문의하기」: 그 작업의 채팅방으로 간다. 방을 못 찾거나 목록을 불러오지 못하면 채팅 목록으로.
 * 사장님 · 학생이 각자의 채팅방 · 목록 주소를 넘겨 같이 쓴다. 찾는 동안 다시 눌러도 한 번만 간다.
 */
export function useOpenJobChat(chatPath: (roomId: string) => string, listPath: string): (jobId: number) => void {
  const navigate = useNavigate();
  const opening = useRef(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  return useCallback(
    (jobId: number) => {
      if (opening.current) return;
      opening.current = true;
      void findJobChatRoomId(jobId).then((roomId) => {
        opening.current = false;
        if (mounted.current) navigate(roomId ? chatPath(roomId) : listPath);
      });
    },
    [chatPath, listPath, navigate],
  );
}
