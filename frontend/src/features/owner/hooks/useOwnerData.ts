import {
  SAMPLE_CHAT_THREADS,
  SAMPLE_PROPOSALS,
  SAMPLE_REQUESTS,
  SAMPLE_WORKS,
} from "../lib/sampleDetails";
import { SAMPLE_OWNER_HOME } from "../lib/sampleHome";
import { SAMPLE_CHATS, SAMPLE_EXPLORE, SAMPLE_NOTIFICATIONS, SAMPLE_PROFILE } from "../lib/sampleTabs";
import type {
  ExploreItem,
  OwnerChatRoom,
  OwnerChatThread,
  OwnerNotification,
  OwnerProfile,
  OwnerProposal,
  OwnerRequest,
  OwnerWork,
  RequestExample,
} from "../types";

/*
 * 사장님 화면 데이터. 지금은 임시 예시 데이터를 돌려준다.
 * 백엔드를 연동할 때 이 안만 API 호출로 바꾸면 화면은 그대로 쓴다.
 */

/** 탐색 목록 (다른 가게의 제안·의뢰) */
export function useOwnerExplore(): ExploreItem[] {
  return SAMPLE_EXPLORE;
}

/** 채팅 목록. 최근 메시지 순 */
export function useOwnerChats(): OwnerChatRoom[] {
  return SAMPLE_CHATS;
}

/** 알림. 최근 것부터 */
export function useOwnerNotifications(): OwnerNotification[] {
  return SAMPLE_NOTIFICATIONS;
}

/** 내 정보 머리 */
export function useOwnerProfile(): OwnerProfile {
  return SAMPLE_PROFILE;
}

/** 작업 하나. 없으면 undefined */
export function useOwnerWork(workId: string | undefined): OwnerWork | undefined {
  return SAMPLE_WORKS.find((work) => work.id === workId);
}

/** 받은 제안 하나 */
export function useOwnerProposal(proposalId: string): OwnerProposal | undefined {
  return SAMPLE_PROPOSALS.find((proposal) => proposal.id === proposalId);
}

/** 보낸 의뢰 하나 (지원자 포함) */
export function useOwnerRequest(requestId: string): OwnerRequest | undefined {
  return SAMPLE_REQUESTS.find((request) => request.id === requestId);
}

/** 채팅방 메시지 */
export function useOwnerChatThread(workId: string): OwnerChatThread | undefined {
  return SAMPLE_CHAT_THREADS.find((thread) => thread.workId === workId);
}

/** 홈 「이런 의뢰는 어때요?」 예시 하나. 의뢰 등록을 이 내용으로 채워 시작한다 */
export function useRequestExample(exampleId: string | undefined): RequestExample | undefined {
  return SAMPLE_OWNER_HOME.examples.find((example) => example.id === exampleId);
}
