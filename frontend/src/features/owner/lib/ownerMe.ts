import { ApiError } from "../../../api/client";
import { uploadSignupPhoto } from "../../signup";
import { fetchOwnerMe, updateOwnerMe } from "../api/meApi";
import type { OwnerMeResponse } from "../api/meApi";

/** 내 정보 (GET /owners/me) */
export type OwnerMe = OwnerMeResponse;

export type OwnerMeResult =
  | { status: "loaded"; data: OwnerMe }
  | { status: "unauthorized" }
  /** 403 — 사장님이 아님 */
  | { status: "forbidden" }
  | { status: "error" };

/** 내 정보를 불러온다 */
export async function loadOwnerMe(): Promise<OwnerMeResult> {
  try {
    return { status: "loaded", data: await fetchOwnerMe() };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
    }
    return { status: "error" };
  }
}

/** 저장할 가게 정보. PUT /owners/me 는 보낸 값으로 모두 바뀌어서 바꾸지 않는 칸도 지금 값을 넣는다 */
export interface OwnerMeChanges {
  storeName: string;
  categoryId: number;
  storeAddress: string;
  description: string;
  /** 지금 사진 주소. 새 사진을 함께 넘기면 그 사진으로 바뀐다 */
  profileImageUrl: string;
}

/** 지금 내 정보 그대로 (사진만 바꿀 때 등) */
export function ownerMeChanges(me: OwnerMe): OwnerMeChanges {
  return {
    storeName: me.storeName,
    categoryId: me.categoryId,
    storeAddress: me.storeAddress ?? "",
    description: me.description ?? "",
    profileImageUrl: me.profileImageUrl ?? "",
  };
}

export type OwnerMeSaveResult =
  /** 저장한 사진 주소 (새 사진이면 올린 주소) */
  | { status: "saved"; profileImageUrl: string }
  | {
      status:
        | "unauthorized"
        | "forbidden"
        /** 새 사진을 올리지 못함 */
        | "photoFailed"
        /** 400 — 적은 내용 · 업종 확인 (CATEGORY_400 포함) */
        | "invalidInput"
        /** 5xx · 네트워크 */
        | "error";
    };

/**
 * 가게 정보를 저장한다 (PUT /owners/me). 새 사진이 있으면 먼저 프로필 사진(PROFILE)으로 올리고
 * 그 주소로 바꾼다. 글자 칸은 앞뒤 빈칸을 뺀다.
 */
export async function saveOwnerMe(changes: OwnerMeChanges, photo?: File): Promise<OwnerMeSaveResult> {
  let profileImageUrl = changes.profileImageUrl;
  if (photo) {
    const uploaded = await uploadSignupPhoto(photo);
    if (uploaded.status === "unauthorized") return { status: "unauthorized" };
    if (uploaded.status !== "uploaded") return { status: "photoFailed" };
    profileImageUrl = uploaded.imageUrl;
  }
  try {
    await updateOwnerMe({
      storeName: changes.storeName.trim(),
      categoryId: changes.categoryId,
      profileImageUrl,
      storeAddress: changes.storeAddress.trim(),
      description: changes.description.trim(),
    });
    return { status: "saved", profileImageUrl };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      if (error.status === 403) return { status: "forbidden" };
      if (error.status === 400) return { status: "invalidInput" };
    }
    return { status: "error" };
  }
}
