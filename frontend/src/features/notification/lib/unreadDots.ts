import { refreshCachedLists } from "../../../api/client";
import { getAccessToken } from "../../../api/tokens";
import { chatReadsSettled, onChatReadSettled } from "../../chat";
import { fetchUnreadStatus } from "../api/notificationApi";
import { notificationReadsSettled, onNotificationReadSettled } from "./readSync";

/*
 * 앱바 종 점과 탭바 「채팅」 점 (ADR 0078). 탭 화면마다 따로 부르지 않고 여기 하나가
 * GET /me/notifications/unread-count 한 번으로 둘 다 받아 나눠 준다.
 * 탭 화면이 떠 있는 동안: 마지막으로 받은 지 2초가 지났으면 받고, 보이는 동안 응답 후 2초 뒤,
 * 탭이 다시 보일 때, 읽음 처리(알림 · 채팅)가 끝날 때 받는다. 탭이 안 보이면 멈춘다.
 * 그래서 홈 → 탐색 → 채팅처럼 탭을 옮길 때는 다시 부르지 않는다.
 * 받은 답은 계정(액세스 토큰)마다라, 둘러보기 역할을 바꾸면 비우고 새로 받는다.
 */

export interface UnreadDots {
  /** 안 읽은 알림이 하나라도 있는지 (종 점) */
  notification: boolean;
  /** 안 읽은 채팅 메시지가 있는 방이 있는지 (탭바 채팅 점) */
  chat: boolean;
}

/** 다시 받는 간격 */
const POLL_MS = 2_000;
const NONE: UnreadDots = { notification: false, chat: false };

let dots: UnreadDots = NONE;
/** 이 답이 누구 것인지 (액세스 토큰) */
let scope: string | undefined;
/** 마지막으로 답을 받은 때. 실패해도 적어서, 실패한 뒤 탭을 옮길 때마다 다시 부르지 않는다 */
let loadedAt = 0;
/** 마지막으로 받은 안 읽은 알림 수 (늘었는지 보려고) */
let seenUnreadCount: number | undefined;
/** 겹쳐 부르면 마지막으로 부른 답만 쓴다 */
let latest = 0;
let loading = false;
let timer: number | undefined;
let stopTimer: number | undefined;
let stopListening: (() => void) | undefined;
const listeners = new Set<() => void>();

const visible = () => document.visibilityState === "visible";

function setDots(next: UnreadDots): void {
  if (next.notification === dots.notification && next.chat === dots.chat) return;
  dots = next;
  listeners.forEach((listener) => listener());
}

function refresh(): void {
  window.clearTimeout(timer);
  timer = undefined;
  const call = ++latest;
  loading = true;
  void Promise.all([notificationReadsSettled(), chatReadsSettled()])
    .then(fetchUnreadStatus)
    .then(
      (status) => status,
      () => undefined,
    )
    .then((status) => {
      if (call !== latest) return;
      loading = false;
      loadedAt = Date.now();
      if (status) {
        // 안 읽은 알림이 늘면 상대가 무언가 바꾼 것이라, 기억해 둔 목록을 뒤에서 다시 받는다 (ADR 0077)
        if (seenUnreadCount !== undefined && status.unreadCount > seenUnreadCount) refreshCachedLists();
        seenUnreadCount = status.unreadCount;
      }
      // 불러오지 못하면 점을 숨긴다
      setDots({ notification: (status?.unreadCount ?? 0) > 0, chat: status?.hasUnreadChat ?? false });
      if (stopListening && visible()) timer = window.setTimeout(refresh, POLL_MS);
    });
}

/** 받은 지 2초가 지났으면 지금(탭이 안 보여도), 아니면 보이는 동안 2초가 되는 때에 받는다 */
function schedule(): void {
  if (loading) return;
  window.clearTimeout(timer);
  timer = undefined;
  const wait = loadedAt + POLL_MS - Date.now();
  if (wait <= 0) refresh();
  else if (visible()) timer = window.setTimeout(refresh, wait);
}

function onVisibility(): void {
  if (visible()) {
    schedule();
  } else {
    window.clearTimeout(timer);
    timer = undefined;
  }
}

function start(): void {
  const current = getAccessToken() ?? "";
  if (current !== scope) {
    // 다른 계정이면 앞 계정의 점과 진행 중인 답을 버린다
    scope = current;
    latest += 1;
    loading = false;
    loadedAt = 0;
    seenUnreadCount = undefined;
    setDots(NONE);
  }
  if (!stopListening) {
    document.addEventListener("visibilitychange", onVisibility);
    stopListening = () => document.removeEventListener("visibilitychange", onVisibility);
  }
  schedule();
}

/**
 * 읽음 처리가 끝나면 탭 화면이 떠 있을 때는 바로 받고, 채팅방 · 알림 화면처럼 탭 화면이 없을 때는
 * 다음 탭 화면이 들어오자마자 받도록 해 둔다 (2초를 기다리지 않는다).
 */
function onReadSettled(): void {
  if (stopListening) {
    refresh();
  } else {
    // 읽기 전에 보낸 답은 버린다
    latest += 1;
    loading = false;
    loadedAt = 0;
  }
}

onChatReadSettled(onReadSettled);
onNotificationReadSettled(onReadSettled);

function stop(): void {
  stopTimer = undefined;
  window.clearTimeout(timer);
  timer = undefined;
  stopListening?.();
  stopListening = undefined;
}

/**
 * 탭 화면이 점을 듣기 시작한다. 마지막 탭 화면이 떠나면 멈추는데, 탭을 옮길 때처럼 바로 다음
 * 탭 화면이 들어오면 멈추지 않고 이어 간다.
 */
export function subscribeUnreadDots(listener: () => void): () => void {
  listeners.add(listener);
  window.clearTimeout(stopTimer);
  stopTimer = undefined;
  start();
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) stopTimer = window.setTimeout(stop, 0);
  };
}

export function getUnreadDots(): UnreadDots {
  return dots;
}
