import { useSyncExternalStore } from "react";
import { todayIsoDate } from "../../../lib/date";
import type { MyProfile, WorkFile } from "../types";

/*
 * 시연 중에 바뀐 상태. 백엔드 연동 전까지 새로고침하면 처음으로 돌아간다.
 * 바뀔 때마다 version 이 올라가 이 상태를 읽는 화면이 다시 그려진다.
 */

/** 프로필 편집에서 고칠 수 있는 값 (이름 · 학교 · 학과는 인증 정보라 못 고친다) */
export type ProfileEdit = Pick<MyProfile, "intro" | "badges" | "certificates" | "portfolioUrl">;

interface Submission {
  files: WorkFile[];
  message: string;
  on: string;
}

export const demo = {
  agreedWorkIds: new Set<string>(),
  readNotificationIds: new Set<string>(),
  declinedWorkIds: new Set<string>(),
  submissions: new Map<string, Submission>(),
  /** 프로필 편집에서 저장한 값 */
  profile: null as ProfileEdit | null,
  /** 내 사진 (내 정보 · 프로필 수정 · 프로필 편집이 같이 본다) */
  profilePhoto: null as File | null,
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
export function useDemoVersion(): number {
  return useSyncExternalStore(subscribe, getVersion);
}

/** 작업 시작 「동의하고 작업 시작하기」 */
export function agreeToWork(workId: string): void {
  demo.agreedWorkIds.add(workId);
  changed();
}

/** 알림을 읽음으로 (알림 화면 · 홈의 안 읽음 점이 같이 본다) */
export function markNotificationsRead(ids: string[]): void {
  ids.forEach((id) => demo.readNotificationIds.add(id));
  changed();
}

/** 작업 시작 「이 조건은 어려워요」 → 거절 (사장님께 작업비가 돌아간다) */
export function declineWork(workId: string): void {
  demo.declinedWorkIds.add(workId);
  changed();
}

/** 초안 · 수정안 제출 */
export function submitWork(workId: string, files: WorkFile[], message: string): void {
  demo.submissions.set(workId, { files, message, on: todayIsoDate() });
  changed();
}

/** 프로필 편집 「저장하기」 */
export function saveMyProfile(edit: ProfileEdit): void {
  demo.profile = edit;
  changed();
}

/** 내 사진 바꾸기 */
export function setMyProfilePhoto(file: File | null): void {
  demo.profilePhoto = file;
  changed();
}

/** 내 사진. 백엔드 연동 전까지 고른 파일을 그대로 미리 보여 준다 */
export function useMyProfilePhoto(): File | null {
  useDemoVersion();
  return demo.profilePhoto;
}
