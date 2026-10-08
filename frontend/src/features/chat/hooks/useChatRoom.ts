import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useObjectUrls } from "../../../hooks/useObjectUrls";
import type { AttachmentType } from "../../../lib/attachmentFormats";
import {
  fetchChatMessage,
  fetchChatMessages,
  fetchChatRoom,
  markChatRead,
  prepareChatAttachmentUpload,
  putChatAttachment,
  sendChatAttachment,
  sendChatText,
} from "../api/chatApi";
import {
  CHAT_LEAVE_MESSAGE,
  attachmentFailureOf,
  chatFailureOf,
  checkAttachment,
  isAttachmentExpired,
  mergeMessages,
  newClientMessageId,
  toChatMessage,
  upsertMessage,
} from "../lib/messages";
import { trackRead } from "../lib/readSync";
import type { ChatFailure, ChatMessage, ChatRoom } from "../types";

/** 보내는 중 · 보내지 못한 첨부. uploadId 가 있으면 저장소에 올리기까지 끝났다 */
interface AttachmentJob {
  file: File;
  type: AttachmentType;
  contentType: string;
  uploadId?: string;
}

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
  /**
   * 사진 · 파일 하나 보내기. 형식 · 크기가 안 맞으면 알림만 띄운다.
   * 준비(POST uploads) → 저장소 PUT → POST messages/attachments 동안 말풍선을 먼저 보인다
   */
  sendAttachment: (file: File) => void;
  /**
   * 보내지 못한 글 · 첨부를 다시 보낸다. 글과 저장소에 올리기까지 끝난 첨부는 같은
   * clientMessageId 로 보내기만 다시, 그 전에 실패한 첨부는 새 clientMessageId 로 준비부터
   */
  resend: (clientMessageId: string) => void;
  /** 만료된 사진 · 파일 주소를 새로 받아 새 탭에서 연다 */
  openExpiredAttachment: (message: ChatMessage) => void;
  /** 만료된 사진 주소를 새로 받아 말풍선 · 크게 보기에 바꿔 끼운다 */
  refreshAttachment: (message: ChatMessage) => void;
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
  /** 보내는 중 · 보내지 못한 첨부 (clientMessageId 별) */
  const attachments = useRef(new Map<string, AttachmentJob>());
  /** 이 화면에서 보낸 첨부의 크기. 서버 메시지에는 크기가 없어 보낸 뒤에도 이것으로 보인다 */
  const sentSizes = useRef(new Map<string, number>());
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

  /** 이 화면에서 보낸 첨부면 크기를 붙인다 */
  const withSentSize = useCallback((message: ChatMessage): ChatMessage => {
    const size = sentSizes.current.get(message.clientMessageId);
    return size === undefined || message.fileSize !== undefined ? message : { ...message, fileSize: size };
  }, []);

  /** 받은 대화 내역을 합치고, 그 안에 저장된 보내는 중 · 실패 글은 뺀다 */
  const receive = useCallback(
    (page: Awaited<ReturnType<typeof fetchChatMessages>>) => {
      if (page.viewerUserId !== undefined) viewerUserId.current = page.viewerUserId;
      const viewer = viewerUserId.current;
      setSaved((current) => mergeMessages(current, page.messages, viewer, mineIds.current).map(withSentSize));
      const savedIds = new Set(page.messages.map((message) => message.clientMessageId));
      savedIds.forEach((id) => attachments.current.delete(id));
      setOutgoing((current) => current.filter((message) => !savedIds.has(message.clientMessageId)));
    },
    [withSentSize],
  );

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

  /** 보내는 중 · 보내지 못한 말풍선 하나를 바꾼다 */
  const patchOutgoing = useCallback((clientMessageId: string, patch: Partial<ChatMessage>) => {
    setOutgoing((current) =>
      current.map((m) => (m.clientMessageId === clientMessageId ? { ...m, ...patch } : m)),
    );
  }, []);

  /** 첨부 하나: 아직 안 올렸으면 준비 → 저장소 PUT, 그다음 메시지로 보낸다 */
  const deliverAttachment = useCallback(
    async (clientMessageId: string) => {
      const job = attachments.current.get(clientMessageId);
      if (!job) return;
      try {
        if (job.uploadId === undefined) {
          const upload = await prepareChatAttachmentUpload(roomId, {
            type: job.type,
            fileName: job.file.name,
            contentType: job.contentType,
            size: job.file.size,
          });
          await putChatAttachment(upload, job.file);
          job.uploadId = upload.uploadId;
        }
        const response = await sendChatAttachment(roomId, {
          clientMessageId,
          type: job.type,
          uploadId: job.uploadId,
        });
        attachments.current.delete(clientMessageId);
        if (!mounted.current) return;
        setSaved((current) =>
          upsertMessage(current, withSentSize(toChatMessage(response, viewerUserId.current, true))),
        );
        setOutgoing((current) => current.filter((m) => m.clientMessageId !== clientMessageId));
      } catch (error) {
        const failure = chatFailureOf(error);
        if (failure !== "error") {
          leave(failure);
          return;
        }
        const next = attachmentFailureOf(error);
        if (next.action === "drop") {
          attachments.current.delete(clientMessageId);
          if (!mounted.current) return;
          setOutgoing((current) => current.filter((m) => m.clientMessageId !== clientMessageId));
          window.alert(next.message);
          return;
        }
        // 올린 파일을 다시 쓸 수 없으면 다시 보낼 때 준비부터 한다
        if (next.restart) job.uploadId = undefined;
        if (!mounted.current) return;
        patchOutgoing(clientMessageId, { status: "failed", failureReason: next.reason });
      }
    },
    [roomId, leave, withSentSize, patchOutgoing],
  );

  const sendAttachment = useCallback(
    (file: File) => {
      const check = checkAttachment(file);
      if (!check.ok) {
        window.alert(check.message);
        return;
      }
      const clientMessageId = newClientMessageId();
      mineIds.current.add(clientMessageId);
      sentSizes.current.set(clientMessageId, file.size);
      attachments.current.set(clientMessageId, { file, type: check.type, contentType: check.contentType });
      setOutgoing((current) => [
        ...current,
        {
          id: undefined,
          clientMessageId,
          type: check.type,
          content: undefined,
          attachmentName: file.name,
          contentExpiresAt: undefined,
          createdAt: new Date().toISOString(),
          mine: true,
          status: "sending",
          fileSize: file.size,
          file,
        },
      ]);
      void deliverAttachment(clientMessageId);
    },
    [deliverAttachment],
  );

  const resend = useCallback(
    (clientMessageId: string) => {
      const failed = outgoing.find(
        (m) => m.clientMessageId === clientMessageId && m.status === "failed",
      );
      if (!failed) return;

      const job = attachments.current.get(clientMessageId);
      if (job) {
        if (job.uploadId !== undefined) {
          // 저장소에 올리기까지 끝났으면 같은 clientMessageId 로 보내기만 다시
          patchOutgoing(clientMessageId, { status: "sending", failureReason: undefined });
          void deliverAttachment(clientMessageId);
          return;
        }
        // 그 전에 실패했으면 새 clientMessageId 로 준비부터
        const nextId = newClientMessageId();
        attachments.current.delete(clientMessageId);
        attachments.current.set(nextId, job);
        mineIds.current.add(nextId);
        sentSizes.current.set(nextId, job.file.size);
        patchOutgoing(clientMessageId, { clientMessageId: nextId, status: "sending", failureReason: undefined });
        void deliverAttachment(nextId);
        return;
      }

      if (!failed.content) return;
      const text = failed.content;
      patchOutgoing(clientMessageId, { status: "sending" });
      void deliver(clientMessageId, text);
    },
    [outgoing, deliver, deliverAttachment, patchOutgoing],
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

  const refreshAttachment = useCallback(
    (message: ChatMessage) => {
      if (message.id === undefined) return;
      void fetchChatMessage(roomId, message.id).then(
        (response) => {
          const fresh = toChatMessage(response, viewerUserId.current, message.mine);
          if (mounted.current) setSaved((current) => upsertMessage(current, fresh));
        },
        (error: unknown) => {
          const failure = chatFailureOf(error);
          if (failure !== "error") leave(failure);
        },
      );
    },
    [roomId, leave],
  );

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  // 보내는 중 · 보내지 못한 사진은 고른 파일로 미리 보인다
  const outgoingImages = useMemo(
    () =>
      outgoing.flatMap((m) => (m.type === "IMAGE" && m.file ? [{ id: m.clientMessageId, file: m.file }] : [])),
    [outgoing],
  );
  const imageFiles = useMemo(() => outgoingImages.map((image) => image.file), [outgoingImages]);
  const previewUrls = useObjectUrls(imageFiles);

  const messages = useMemo(() => {
    const previews = new Map(outgoingImages.map((image, i) => [image.id, previewUrls[i]]));
    const pending = outgoing.map((m) => {
      const preview = previews.get(m.clientMessageId);
      return preview ? { ...m, content: preview } : m;
    });
    return [...saved, ...pending];
  }, [saved, outgoing, outgoingImages, previewUrls]);

  return { load, messages, reload, send, sendAttachment, resend, openExpiredAttachment, refreshAttachment };
}
