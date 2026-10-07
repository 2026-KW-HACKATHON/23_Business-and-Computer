import { ApiError } from "../../../api/client";
import { STORE_CATEGORIES } from "../../../types/storeCategory";
import type { StoreCategory } from "../../../types/storeCategory";
import { fetchBusinessCategories, uploadSignupPhoto } from "../../signup";
import type { BusinessCategory } from "../../signup";
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

export type StoreCategoriesResult =
  | { status: "loaded"; data: BusinessCategory[] }
  | { status: "unauthorized" }
  | { status: "error" };

/** 업종 목록 (GET /business-categories). 업종 칩 이름과 서버 id 를 잇는다 */
export async function loadStoreCategories(): Promise<StoreCategoriesResult> {
  try {
    return { status: "loaded", data: await fetchBusinessCategories() };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "error" };
  }
}

/** 업종 칩의 서버 id. 목록에 없으면 undefined */
export function storeCategoryId(categories: BusinessCategory[], category: StoreCategory): number | undefined {
  return categories.find((c) => c.name === category)?.id;
}

/** 가게 정보 수정 칸. 주소는 서버에 한 칸이라 불러올 때는 「가게 주소」에 다 넣는다 */
export interface OwnerStoreForm {
  storeName: string;
  /** 업종 칩 11개 중 하나. 서버 업종이 칩에 없으면 비어 있다 */
  category?: StoreCategory;
  address: string;
  addressDetail: string;
  intro: string;
}

export function ownerStoreForm(me: OwnerMe, categories: BusinessCategory[]): OwnerStoreForm {
  const name = categories.find((c) => c.id === me.categoryId)?.name;
  return {
    storeName: me.storeName,
    category: STORE_CATEGORIES.find((category) => category === name),
    address: me.storeAddress ?? "",
    addressDetail: "",
    intro: me.description ?? "",
  };
}

/** 「가게 주소」와 「상세 주소」를 한 줄로 (서버 storeAddress) */
export function storeAddressOf(form: OwnerStoreForm): string {
  return [form.address.trim(), form.addressDetail.trim()].filter(Boolean).join(" ");
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
