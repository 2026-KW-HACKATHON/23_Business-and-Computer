import { useSyncExternalStore } from "react";

/*
 * 사장님 알림 읽음 표시. 알림 화면 · 메인 탭 앱바의 종 점이 같이 본다.
 * 백엔드 연동 전까지 새로고침하면 처음으로 돌아간다.
 */

const readIds = new Set<string>();
let version = 0;
const listeners = new Set<() => void>();

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

const getVersion = () => version;

/** 읽은 알림 id. 바뀌면 다시 그린다 */
export function useReadNotificationIds(): ReadonlySet<string> {
  useSyncExternalStore(subscribe, getVersion);
  return readIds;
}

/** 알림을 읽음으로 (하나 누르기 · 「모두 읽음」) */
export function markOwnerNotificationsRead(ids: string[]): void {
  ids.forEach((id) => readIds.add(id));
  version += 1;
  listeners.forEach((listener) => listener());
}
