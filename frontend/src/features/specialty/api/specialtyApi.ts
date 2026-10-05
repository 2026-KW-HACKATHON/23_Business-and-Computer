import { apiFetch } from "../../../api/client";
import type { ApiResponse } from "../../../api/client";
import { getAccessToken } from "../../auth";
import type { SpecialtyCategory } from "../types";

/**
 * 토큰을 붙여 보내고 `data` 만 꺼낸다.
 * TODO: 팀장님 공용 API(src/api/client.ts 의 apiData) 머지 후 `apiData<T>(path, init)` 한 줄로 교체.
 */
async function requestData<T>(path: string, init: RequestInit = {}): Promise<T | undefined> {
  const token = getAccessToken();
  const response = await apiFetch<ApiResponse<T>>(path, {
    ...init,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  return response.data;
}

/**
 * GET /specialties — 대분류별 특기 목록 (data 는 배열).
 * 「기타」처럼 비어 있는 분류도 그대로 돌려준다. 거를지는 화면이 정한다 (`selectableCategories`).
 */
export async function fetchSpecialties(): Promise<SpecialtyCategory[]> {
  return (await requestData<SpecialtyCategory[]>("/specialties")) ?? [];
}
