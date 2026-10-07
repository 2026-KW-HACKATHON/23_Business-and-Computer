import { useSyncExternalStore } from "react";

/*
 * 사장님 화면에서 시연 중에 바뀐 상태. 백엔드 연동 전까지 새로고침하면 처음으로 돌아간다.
 * 바뀔 때마다 version 이 올라가 이 상태를 읽는 화면이 다시 그려진다.
 */

export const ownerDemo = {
  /** 읽은 알림 (알림 화면 · 메인 탭 종 점이 같이 본다) */
  readNotificationIds: new Set<string>(),
};

let version = 0;
const listeners = new Set<() => void>();

function changed() {
  version += 1;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

const getVersion = () => version;

/** 상태가 바뀌면 다시 그리게 한다 */
export function useOwnerDemoVersion(): number {
  return useSyncExternalStore(subscribe, getVersion);
}

/** 알림을 읽음으로 (하나 누르기 · 「모두 읽음」) */
export function markOwnerNotificationsRead(ids: string[]): void {
  ids.forEach((id) => ownerDemo.readNotificationIds.add(id));
  changed();
}
