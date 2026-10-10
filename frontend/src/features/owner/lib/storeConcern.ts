import { ApiError } from "../../../api/client";
import { fetchOwnerConcern, resolveOwnerConcern, saveOwnerConcern } from "../api/concernApi";
import type { StoreConcernResponse } from "../api/concernApi";

/** 한 줄 고민 · 자세한 설명 글자 수 (백엔드 StoreConcernSaveRequest 와 같다) */
export const CONCERN_TITLE_MAX = 60;
export const CONCERN_DESCRIPTION_MAX = 500;

/** 사장님 가게의 해결되지 않은 고민 (화면용) */
export interface OwnerConcern {
  title: string;
  /** 자세한 설명. 없으면 빈 글자 */
  description: string;
  /** 분야(특기 대분류). 고르지 않았으면 null */
  categoryId: number | null;
  categoryName: string | null;
  /** 처음 올린 시각 (+09:00). 홈 카드 「○월 ○일에 올렸어요」 */
  createdAt: string;
  /** 올리거나 마지막으로 고친 시각 (+09:00) */
  updatedAt: string;
}

/** 고민 작성 화면의 입력 */
export interface OwnerConcernForm {
  title: string;
  description: string;
  categoryId: number | null;
}

export type OwnerConcernLoadResult =
  | { status: "loaded"; concern: OwnerConcern | null }
  | { status: "unauthorized" }
  | { status: "error" };

export type OwnerConcernSaveResult =
  | { status: "saved"; concern: OwnerConcern }
  | { status: "unauthorized" }
  | { status: "forbidden" }
  | { status: "invalidInput" }
  | { status: "conflict" }
  | { status: "error" };

export type OwnerConcernResolveResult =
  | { status: "resolved" }
  | { status: "unauthorized" }
  | { status: "forbidden" }
  | { status: "error" };

function toConcern(response: StoreConcernResponse): OwnerConcern {
  return {
    title: response.title,
    description: response.description ?? "",
    categoryId: response.specialtyCategory?.id ?? null,
    categoryName: response.specialtyCategory?.name ?? null,
    createdAt: response.createdAt,
    updatedAt: response.updatedAt,
  };
}

/** 작성 화면을 지금 고민으로 채운다. 고민이 없으면 빈 칸 */
export function concernForm(concern: OwnerConcern | null): OwnerConcernForm {
  return {
    title: concern?.title ?? "",
    description: concern?.description ?? "",
    categoryId: concern?.categoryId ?? null,
  };
}

/** GET /owners/me/concern. 401 은 unauthorized, 그 밖의 실패(사장님이 아님 포함)는 error */
export async function loadOwnerConcern(): Promise<OwnerConcernLoadResult> {
  try {
    const response = await fetchOwnerConcern();
    return { status: "loaded", concern: response ? toConcern(response) : null };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "error" };
  }
}

/** PUT /owners/me/concern. 한 줄 · 설명은 앞뒤 공백을 빼고, 빈 설명은 지운다(null) */
export async function saveConcern(form: OwnerConcernForm): Promise<OwnerConcernSaveResult> {
  const description = form.description.trim();
  try {
    const response = await saveOwnerConcern({
      title: form.title.trim(),
      description: description === "" ? null : description,
      specialtyCategoryId: form.categoryId,
    });
    return { status: "saved", concern: toConcern(response) };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
      if (error.status === 400) return { status: "invalidInput" };
      if (error.status === 409) return { status: "conflict" };
    }
    return { status: "error" };
  }
}

/** DELETE /owners/me/concern. 이미 내려간 고민(404)도 해결된 것으로 본다 */
export async function resolveConcern(): Promise<OwnerConcernResolveResult> {
  try {
    await resolveOwnerConcern();
    return { status: "resolved" };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
      if (error.status === 404) return { status: "resolved" };
    }
    return { status: "error" };
  }
}
