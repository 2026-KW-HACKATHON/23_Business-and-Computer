import type { MainTab } from "../../../components";

/** 사장님 화면 주소. 아직 없는 화면은 App.tsx 에서 사장님 홈으로 돌려보낸다 */
export const OWNER_PATHS = {
  home: "/owner",
  explore: "/owner/explore",
  chats: "/owner/chats",
  chat: (id: string) => `/owner/chats/${id}`,
  me: "/owner/me",
  notifications: "/owner/notifications",
  newRequest: "/owner/requests/new",
  requestExamples: "/owner/requests/examples",
  request: (id: string) => `/owner/requests/${id}`,
  requestResult: (id: string) => `/owner/requests/${id}/result`,
  proposal: (id: string) => `/owner/proposals/${id}`,
};

export const OWNER_TAB_PATHS: Record<MainTab, string> = {
  home: OWNER_PATHS.home,
  explore: OWNER_PATHS.explore,
  chat: OWNER_PATHS.chats,
};
