import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchChatRooms } from "../api/chatApi";
import { chatFailureOf } from "../lib/messages";
import { onReadSettled, readsSettled } from "../lib/readSync";
import type { ChatRoom } from "../types";

export type ChatRoomsLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; rooms: ChatRoom[] };

/**
 * 내 채팅방 목록 (GET /me/chat-rooms). 화면에 들어올 때, 탭이 다시 보일 때, 읽음 처리가
 * 끝날 때 불러온다. 보내는 중인 읽음 처리가 있으면 끝난 뒤에 불러온다.
 * 다시 보일 때는 지금 목록을 그대로 둔 채 바꾸고, 그때 실패하면 지금 목록을 둔다.
 * 401 은 /login 으로 보낸다. 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useChatRooms(): { load: ChatRoomsLoad; reload: () => void } {
  const navigate = useNavigate();
  const [load, setLoad] = useState<ChatRoomsLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    let latest = 0;
    const fetchRooms = (quiet: boolean) => {
      // 겹쳐 부르면 마지막으로 부른 답만 쓴다
      const call = ++latest;
      const current = () => active && call === latest;
      void readsSettled().then(fetchChatRooms).then(
        (rooms) => {
          if (current()) setLoad({ status: "loaded", rooms });
        },
        (error: unknown) => {
          if (!current()) return;
          if (chatFailureOf(error) === "unauthorized") {
            navigate("/login", { replace: true });
          } else if (!quiet) {
            setLoad({ status: "error" });
          } else {
            setLoad((current) => (current.status === "loaded" ? current : { status: "error" }));
          }
        },
      );
    };
    const onVisibility = () => {
      if (document.visibilityState === "visible") fetchRooms(true);
    };

    fetchRooms(false);
    document.addEventListener("visibilitychange", onVisibility);
    const stopListening = onReadSettled(() => fetchRooms(true));
    return () => {
      active = false;
      document.removeEventListener("visibilitychange", onVisibility);
      stopListening();
    };
  }, [request, navigate]);

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  return { load, reload };
}
