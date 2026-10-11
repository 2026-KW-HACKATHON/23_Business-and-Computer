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

/** 목록을 다시 불러오는 간격. 채팅방(useChatRoom)과 같다 */
const POLL_MS = 3_000;

/**
 * 내 채팅방 목록 (GET /me/chat-rooms). 화면에 들어올 때, 보이는 동안 3초마다, 탭이 다시 보일 때,
 * 읽음 처리가 끝날 때 불러온다. 탭이 안 보이면 멈춘다. 보내는 중인 읽음 처리가 있으면 끝난 뒤에 불러온다.
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
    let timer: number | undefined;
    const visible = () => document.visibilityState === "visible";
    const stopPolling = () => {
      window.clearTimeout(timer);
      timer = undefined;
    };
    // 답을 받은 뒤에 다음 차례를 잡아, 느린 응답이 겹쳐 쌓이지 않는다
    const schedule = () => {
      stopPolling();
      if (active && visible()) timer = window.setTimeout(() => fetchRooms(true), POLL_MS);
    };
    const fetchRooms = (quiet: boolean) => {
      stopPolling();
      // 겹쳐 부르면 마지막으로 부른 답만 쓴다
      const call = ++latest;
      const current = () => active && call === latest;
      void readsSettled().then(fetchChatRooms).then(
        (rooms) => {
          if (!current()) return;
          setLoad({ status: "loaded", rooms });
          schedule();
        },
        (error: unknown) => {
          if (!current()) return;
          if (chatFailureOf(error) === "unauthorized") {
            navigate("/login", { replace: true });
            return;
          }
          if (!quiet) {
            setLoad({ status: "error" });
          } else {
            setLoad((current) => (current.status === "loaded" ? current : { status: "error" }));
          }
          schedule();
        },
      );
    };
    const onVisibility = () => {
      if (visible()) fetchRooms(true);
      else stopPolling();
    };

    fetchRooms(false);
    document.addEventListener("visibilitychange", onVisibility);
    const stopListening = onReadSettled(() => fetchRooms(true));
    return () => {
      active = false;
      stopPolling();
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
