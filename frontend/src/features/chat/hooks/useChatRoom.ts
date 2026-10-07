import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  fetchChatMessage,
  fetchChatMessages,
  fetchChatRoom,
  markChatRead,
  sendChatText,
} from "../api/chatApi";
import {
  CHAT_LEAVE_MESSAGE,
  chatFailureOf,
  isAttachmentExpired,
  mergeMessages,
  newClientMessageId,
  toChatMessage,
  upsertMessage,
} from "../lib/messages";
import { trackRead } from "../lib/readSync";
import type { ChatFailure, ChatMessage, ChatRoom } from "../types";

/** 채팅방 안에서 대화 내역을 다시 불러오는 간격 */
const POLL_MS = 3_000;

export type ChatRoomLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; room: ChatRoom };

export interface ChatRoomState {
  load: ChatRoomLoad;
  /** 저장된 메시지 다음에 보내는 중 · 보내지 못한 글 */
  messages: ChatMessage[];
  reload: () => void;
  /** 글 보내기. 보내는 동안 말풍선을 먼저 보인다 */
  send: (text: string) => void;
  /** 보내지 못한 글을 같은 clientMessageId 로 다시 보낸다 */
  resend: (clientMessageId: string) => void;
  /** 만료된 사진 · 파일 주소를 새로 받아 새 탭에서 연다 */
  openExpiredAttachment: (message: ChatMessage) => void;
}

/**
 * 채팅방 하나 (GET /chat-rooms/{roomId} + 대화 내역).
 * - 대화 내역은 3초마다 다시 불러오고, 탭이 안 보이면 멈췄다가 다시 보이면 바로 불러온다
 * - 마지막 메시지가 바뀌면 그 id 로 읽음 처리한다 (들어올 때 · 새 메시지를 받을 때)
 * - 401 은 /login, CHAT_403 · CHAT_ROOM_404 는 안내 후 listPath 로 보낸다
 * roomId 가 바뀌면 화면이 key 로 새로 만들어 쓴다.
 */
export function useChatRoom(roomId: string, listPath: string): ChatRoomState {
  const navigate = useNavigate();
  const [load, setLoad] = useState<ChatRoomLoad>({ status: "loading" });
  const [saved, setSaved] = useState<ChatMessage[]>([]);
  const [outgoing, setOutgoing] = useState<ChatMessage[]>([]);
  const [request, setRequest] = useState(0);
  /** 대화 내역이 알려 준 지금 사용자 ID. 내 메시지는 senderUserId 가 이것과 같은 것 */
  const viewerUserId = useRef<string | undefined>(undefined);
  /** 이 화면에서 보낸 글. viewerUserId 를 모를 때만 이것으로 내 메시지를 정한다 */
  const mineIds = useRef(new Set<string>());
  const mounted = useRef(true);
  const left = useRef(false);
  const lastRead = useRef(0);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const leave = useCallback(
    (failure: Exclude<ChatFailure, "error">) => {
      if (left.current || !mounted.current) return;
      left.current = true;
      if (failure === "unauthorized") {
        navigate("/login", { replace: true });
        return;
      }
      window.alert(CHAT_LEAVE_MESSAGE[failure]);
      navigate(listPath, { replace: true });
    },
    [navigate, listPath],
  );

  /** 받은 대화 내역을 합치고, 그 안에 저장된 보내는 중 · 실패 글은 뺀다 */
  const receive = useCallback((page: Awaited<ReturnType<typeof fetchChatMessages>>) => {
    if (page.viewerUserId !== undefined) viewerUserId.current = page.viewerUserId;
    const viewer = viewerUserId.current;
    setSaved((current) => mergeMessages(current, page.messages, viewer, mineIds.current));
    const savedIds = new Set(page.messages.map((message) => message.clientMessageId));
    setOutgoing((current) => current.filter((message) => !savedIds.has(message.clientMessageId)));
  }, []);

  // 처음 불러오기
  useEffect(() => {
    let active = true;
    void Promise.all([fetchChatRoom(roomId), fetchChatMessages(roomId)]).then(
      ([room, page]) => {
        if (!active) return;
        receive(page);
        setLoad({ status: "loaded", room });
      },
      (error: unknown) => {
        if (!active) return;
        const failure = chatFailureOf(error);
        if (failure === "error") setLoad({ status: "error" });
        else leave(failure);
      },
    );
    return () => {
      active = false;
    };
  }, [roomId, request, receive, leave]);

  // 다시 불러오기 (보이는 동안만)
  const loaded = load.status === "loaded";
  useEffect(() => {
    if (!loaded) return;
    let active = true;
    let running = false;
    let timer: number | undefined;
    const visible = () => document.visibilityState === "visible";

    const schedule = () => {
      if (active && !running && timer === undefined && visible()) {
        timer = window.setTimeout(() => void tick(), POLL_MS);
      }
    };
    const tick = async () => {
      timer = undefined;
      running = true;
      try {
        const page = await fetchChatMessages(roomId);
        if (active) receive(page);
      } catch (error) {
        const failure = chatFailureOf(error);
        if (active && failure !== "error") {
          leave(failure);
          return;
        }
      } finally {
        running = false;
      }
      schedule();
    };
    const onVisibility = () => {
      window.clearTimeout(timer);
      timer = undefined;
      if (!visible()) return;
      // 작업 카드(제출 · 마감)도 그사이 바뀌었을 수 있다
      void fetchChatRoom(roomId).then(
        (room) => {
          if (active) setLoad({ status: "loaded", room });
        },
        () => undefined,
      );
      if (!running) void tick();
    };

    schedule();
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      active = false;
      window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [loaded, roomId, receive, leave]);

  // 읽음 처리: 마지막 저장된 메시지가 바뀔 때
  const lastId = saved.length > 0 ? saved[saved.length - 1].id : undefined;
  useEffect(() => {
    if (lastId === undefined || lastId <= lastRead.current) return;
    const before = lastRead.current;
    lastRead.current = lastId;
    const request = markChatRead(roomId, lastId);
    // 채팅 목록 · 탭 점이 이 읽음 처리가 끝난 뒤의 안 읽은 수를 불러온다
    trackRead(request);
    void request.catch(() => {
      // 다음 새 메시지 때 다시 보낸다
      if (lastRead.current === lastId) lastRead.current = before;
    });
  }, [roomId, lastId]);

  const deliver = useCallback(
    async (clientMessageId: string, text: string) => {
      try {
        const response = await sendChatText(roomId, clientMessageId, text);
        if (!mounted.current) return;
        setSaved((current) => upsertMessage(current, toChatMessage(response, viewerUserId.current, true)));
        setOutgoing((current) => current.filter((m) => m.clientMessageId !== clientMessageId));
      } catch (error) {
        const failure = chatFailureOf(error);
        if (failure !== "error") {
          leave(failure);
          return;
        }
        if (!mounted.current) return;
        setOutgoing((current) =>
          current.map((m) => (m.clientMessageId === clientMessageId ? { ...m, status: "failed" } : m)),
        );
      }
    },
    [roomId, leave],
  );

  const send = useCallback(
    (text: string) => {
      const clientMessageId = newClientMessageId();
      mineIds.current.add(clientMessageId);
      setOutgoing((current) => [
        ...current,
        {
          id: undefined,
          clientMessageId,
          type: "TEXT",
          content: text,
          attachmentName: undefined,
          contentExpiresAt: undefined,
          createdAt: new Date().toISOString(),
          mine: true,
          status: "sending",
        },
      ]);
      void deliver(clientMessageId, text);
    },
    [deliver],
  );

  const resend = useCallback(
    (clientMessageId: string) => {
      const failed = outgoing.find(
        (m) => m.clientMessageId === clientMessageId && m.status === "failed",
      );
      if (!failed?.content) return;
      const text = failed.content;
      setOutgoing((current) =>
        current.map((m) => (m.clientMessageId === clientMessageId ? { ...m, status: "sending" } : m)),
      );
      void deliver(clientMessageId, text);
    },
    [outgoing, deliver],
  );

  const openExpiredAttachment = useCallback(
    (message: ChatMessage) => {
      if (message.id === undefined) return;
      // 응답을 기다린 뒤 열면 팝업 차단에 걸려서 빈 탭을 먼저 연다
      const tab = window.open("", "_blank");
      void fetchChatMessage(roomId, message.id).then(
        (response) => {
          const fresh = toChatMessage(response, viewerUserId.current, message.mine);
          if (mounted.current) setSaved((current) => upsertMessage(current, fresh));
          if (fresh.content && !isAttachmentExpired(fresh) && tab) {
            tab.opener = null;
            tab.location.href = fresh.content;
          } else {
            tab?.close();
            window.alert("파일을 열지 못했어요. 잠시 후 다시 시도해 주세요");
          }
        },
        (error: unknown) => {
          tab?.close();
          const failure = chatFailureOf(error);
          if (failure !== "error") leave(failure);
          else window.alert("파일을 열지 못했어요. 잠시 후 다시 시도해 주세요");
        },
      );
    },
    [roomId, leave],
  );

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  const messages = useMemo(() => [...saved, ...outgoing], [saved, outgoing]);

  return { load, messages, reload, send, resend, openExpiredAttachment };
}
