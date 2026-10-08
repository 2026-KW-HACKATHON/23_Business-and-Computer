import { apiData } from "../../../api/client";

/** GET /owners/me 의 답 (OwnerMeResponse). 내 정보 머리 · 요약 카드 · 가게 정보 수정이 쓴다 */
export interface OwnerMeResponse {
  ownerProfileId: number;
  /** 가게 사진(프로필 사진). 없으면 회색 원 */
  profileImageUrl?: string | null;
  /** 사장님 이름 */
  name: string;
  /** 사업자 인증 때 적은 대표자 이름. 없을 수 있다 */
  representativeName?: string | null;
  /** 사업자등록번호 숫자 10자리 ("1234567890") */
  businessNumber?: string | null;
  storeName: string;
  storeAddress?: string | null;
  /** 업종 id (GET /business-categories) */
  categoryId: number;
  /** 가게 소개 */
  description?: string | null;
  sentJobCount: number;
  receivedProposalCount: number;
  inProgressJobCount: number;
  completedJobCount: number;
}

/**
 * PUT /owners/me 본문 (OwnerMeUpdateRequest). 보낸 값으로 모두 바뀌므로(없으면 지운다)
 * 바꾸지 않는 칸도 지금 값을 보낸다. 상호명은 꼭, 255자까지. 사진 주소는 http(s) 주소나 빈 값
 */
export interface OwnerMeUpdateRequest {
  storeName: string;
  categoryId: number;
  profileImageUrl: string;
  storeAddress: string;
  description: string;
}

/** GET /owners/me — 로그인한 사장님의 가게 정보와 활동 수 */
export async function fetchOwnerMe(): Promise<OwnerMeResponse> {
  const data = await apiData<OwnerMeResponse | undefined>("/owners/me");
  if (!data) throw new Error("Owner me response has no data");
  return data;
}

/** PUT /owners/me — 가게 정보 저장. 없는 업종이면 400 CATEGORY_400, 사장님이 아니면 403. 답에는 데이터가 없다 */
export async function updateOwnerMe(request: OwnerMeUpdateRequest): Promise<void> {
  await apiData<unknown>("/owners/me", { method: "PUT", body: JSON.stringify(request) });
}
