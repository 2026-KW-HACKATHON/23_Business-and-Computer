import type { MainTab } from "../../../components";

/** 내 활동 탭 */
export type StudentActivityTab = "applied" | "proposals" | "inProgress" | "done";

/** 프로필 편집을 열 때 먼저 보여 줄 칸 (「기본 정보 수정」은 맨 위) */
export type ProfileEditSection = "badges" | "certificates";

/**
 * 학생 화면 주소 (노션 「페이지 주소 정리」 · 「화면 상태 전환표」 기준).
 * 아직 없는 화면은 App.tsx 에서 학생 홈으로 돌려보낸다.
 */
export const STUDENT_PATHS = {
  home: "/student",
  explore: "/student/explore",
  exploreStores: "/student/explore/stores",
  peerProposal: (id: string) => `/student/explore/proposals/${id}`,
  chats: "/student/chats",
  chat: (roomId: string) => `/student/chats/${roomId}`,
  notifications: "/student/notifications",
  me: "/student/me",
  profile: "/student/me/profile",
  profileEdit: "/student/me/profile/edit",
  settlements: "/student/me/settlements",
  portfolio: "/student/me/portfolio",
  activity: (tab: StudentActivityTab) => `/student/activity?tab=${tab}`,
  requestFull: (id: string) => `/student/requests/${id}/full`,
  apply: (id: string) => `/student/requests/${id}/apply`,
  newProposal: "/student/proposals/new",
  newProposalStep: (step: number) => `/student/proposals/new/${step}`,
  newProposalDone: "/student/proposals/new/done",
  proposal: (id: string) => `/student/proposals/${id}`,
  /** 수락돼 의뢰서가 온 제안의 작업 시작 (조건 확인 · 동의) */
  proposalStart: (id: string) => `/student/proposals/${id}/start`,
  workSubmit: (id: string) => `/student/works/${id}/submit`,
  workRevision: (id: string) => `/student/works/${id}/revision`,
  workRevisionSubmit: (id: string) => `/student/works/${id}/revision/submit`,
  workSubmitted: (id: string) => `/student/works/${id}/submitted`,
  /** 지난 초안 · 수정안 (읽기 전용) */
  workSubmission: (id: string, submissionId: string) => `/student/works/${id}/submissions/${submissionId}`,
  /** 지난 결과물에 받은 수정 요청 (읽기 전용) */
  workSubmissionRequest: (id: string, submissionId: string) =>
    `/student/works/${id}/submissions/${submissionId}/request`,
  workResult: (id: string) => `/student/works/${id}/result`,
  workReview: (id: string) => `/student/works/${id}/review`,
  workCanceled: (id: string) => `/student/works/${id}/canceled`,
  workHistory: (id: string) => `/student/works/${id}/history`,
};

export const STUDENT_TAB_PATHS: Record<MainTab, string> = {
  home: STUDENT_PATHS.home,
  explore: STUDENT_PATHS.explore,
  chat: STUDENT_PATHS.chats,
};
