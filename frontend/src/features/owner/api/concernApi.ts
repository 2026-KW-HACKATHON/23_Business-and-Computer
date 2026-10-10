import { apiData } from "../../../api/client";

/** GET · PUT /owners/me/concern 의 답 (StoreConcernResponse). 시각은 +09:00 */
export interface StoreConcernResponse {
  concernId: number;
  title: string;
  /** 자세한 설명. 없으면 null */
  description: string | null;
  /** 분야(특기 대분류). 고르지 않았으면 null */
  specialtyCategory: { id: number; name: string } | null;
  createdAt: string;
  updatedAt: string;
}

/** PUT /owners/me/concern 본문. 한 줄은 꼭(60자까지), 설명(500자까지) · 분야는 null 이면 지운다 */
export interface StoreConcernSaveRequest {
  title: string;
  description: string | null;
  specialtyCategoryId: number | null;
}

/** GET /owners/me/concern — 해결되지 않은 가게 고민. 없으면 data 없이 와서 null */
export async function fetchOwnerConcern(): Promise<StoreConcernResponse | null> {
  return (await apiData<StoreConcernResponse | undefined>("/owners/me/concern")) ?? null;
}

/**
 * PUT /owners/me/concern — 고민을 올리거나 해결되지 않은 고민을 통째로 고친다.
 * 없는 분야면 400 SPECIALTY_CATEGORY_400, 다른 곳에서 동시에 처음 올리면 409 STORE_CONCERN_409
 */
export async function saveOwnerConcern(request: StoreConcernSaveRequest): Promise<StoreConcernResponse> {
  const data = await apiData<StoreConcernResponse | undefined>("/owners/me/concern", {
    method: "PUT",
    body: JSON.stringify(request),
  });
  if (!data) throw new Error("Owner concern response has no data");
  return data;
}

/** DELETE /owners/me/concern — 「해결됐어요」. 해결할 고민이 없으면 404 STORE_CONCERN_404 */
export async function resolveOwnerConcern(): Promise<void> {
  await apiData<unknown>("/owners/me/concern", { method: "DELETE" });
}
