import { useSyncExternalStore } from "react";
import type { OwnerRequest, OwnerStore } from "../types";

/*
 * 사장님 화면에서 시연 중에 바뀐 상태. 백엔드 연동 전까지 새로고침하면 처음으로 돌아간다.
 * 바뀔 때마다 version 이 올라가 이 상태를 읽는 화면이 다시 그려진다.
 */

export const ownerDemo = {
  /** 읽은 알림 (알림 화면 · 메인 탭 종 점이 같이 본다) */
  readNotificationIds: new Set<string>(),
  /** 의뢰 등록으로 새로 올린 의뢰. 최근 것부터 */
  registeredRequests: [] as OwnerRequest[],
  /** 가게 정보 수정에서 저장한 값 */
  store: null as OwnerStore | null,
  /** 가게 사진 (내 정보 · 가게 정보 수정이 같이 본다) */
  storePhoto: null as File | null,
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

/** 의뢰 등록 3/3 「의뢰 등록하기」 */
export function registerOwnerRequest(request: OwnerRequest): void {
  ownerDemo.registeredRequests = [request, ...ownerDemo.registeredRequests];
  changed();
}

/** 가게 정보 수정 「저장하기」 */
export function saveOwnerStore(store: OwnerStore): void {
  ownerDemo.store = store;
  changed();
}

/** 가게 사진 바꾸기 */
export function setOwnerStorePhoto(file: File | null): void {
  ownerDemo.storePhoto = file;
  changed();
}

/** 가게 사진. 백엔드 연동 전까지 고른 파일을 그대로 미리 보여 준다 */
export function useOwnerStorePhoto(): File | null {
  useOwnerDemoVersion();
  return ownerDemo.storePhoto;
}
