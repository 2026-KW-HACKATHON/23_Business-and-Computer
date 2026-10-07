import type { MainTab } from "../../../components";

/** 내 활동 탭 */
export type ActivityTab = "sent" | "proposals" | "inProgress" | "done";

/**
 * 사장님 화면 주소 (노션 「화면 상태 전환표」 기준).
 * 아직 없는 화면은 App.tsx 에서 사장님 홈으로 돌려보낸다.
 */
export const OWNER_PATHS = {
  home: "/owner",
  explore: "/owner/explore",
  chats: "/owner/chats",
  chat: (roomId: string) => `/owner/chats/${roomId}`,
  notifications: "/owner/notifications",
  me: "/owner/me",
  store: "/owner/me/store",
  payments: "/owner/me/payments",
  activity: (tab: ActivityTab) => `/owner/requests?tab=${tab}`,
  newRequest: "/owner/requests/new",
  newRequestStep: (step: number) => `/owner/requests/new/${step}`,
  newRequestDone: "/owner/requests/new/done",
  request: (id: string) => `/owner/requests/${id}`,
  requestCancel: (id: string) => `/owner/requests/${id}/cancel`,
  requestApplicants: (id: string) => `/owner/requests/${id}/applicants`,
  /** 지원자 한 명의 프로필 (의뢰 id · 지원서 id) */
  applicantProfile: (requestId: string, applicationId: string) =>
    `/owner/requests/${requestId}/applicants/${applicationId}`,
  proposal: (id: string) => `/owner/proposals/${id}`,
  /** 이 학생에게 맡기기 (의뢰 id · 지원서 id) */
  assign: (requestId: string, applicationId: string) =>
    `/owner/requests/${requestId}/assign/${applicationId}`,
  /** 고른 지원자에게 맡기는 안전결제 (의뢰 id · 지원서 id) */
  assignPay: (requestId: string, applicationId: string) =>
    `/owner/requests/${requestId}/assign/${applicationId}/pay`,
  proposalAccept: (id: string) => `/owner/proposals/${id}/accept`,
  workCheck: (workId: string) => `/owner/works/${workId}/check`,
  workRevision: (workId: string) => `/owner/works/${workId}/revision`,
  workCancel: (workId: string) => `/owner/works/${workId}/cancel`,
  workCanceled: (workId: string) => `/owner/works/${workId}/canceled`,
  workReview: (workId: string) => `/owner/works/${workId}/review`,
  workReviewDone: (workId: string) => `/owner/works/${workId}/review/done`,
  workResult: (workId: string) => `/owner/works/${workId}/result`,
  exploreProposal: (id: string) => `/explore/proposals/${id}`,
  exploreRequest: (id: string) => `/explore/requests/${id}`,
};

export const OWNER_TAB_PATHS: Record<MainTab, string> = {
  home: OWNER_PATHS.home,
  explore: OWNER_PATHS.explore,
  chat: OWNER_PATHS.chats,
};
