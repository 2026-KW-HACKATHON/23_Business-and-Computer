import { ApiError } from "../../../api/client";
import { saveAccessToken } from "../../auth";
import { fetchBusinessCategories, registerOwner, uploadStoreImage } from "../api/signupApi";
import type { StoreCategory } from "../../../types/storeCategory";
import type {
  BusinessCategoryLookup,
  OwnerRegisterResult,
  OwnerRegistrationRequest,
  OwnerSignupDraft,
  PhotoUploadResult,
} from "../types";

/** YYYYMMDD → YYYY-MM-DD (openedAt 은 LocalDate ISO 형식만 받는다) */
function toIsoDate(yyyymmdd: string): string {
  return `${yyyymmdd.slice(0, 4)}-${yyyymmdd.slice(4, 6)}-${yyyymmdd.slice(6, 8)}`;
}

/**
 * 가입 1/3 에서 고른 업종 이름으로 서버 업종 id 를 찾는다.
 * 서버가 id 로만 받아서 GET /business-categories 목록에서 같은 이름을 고른다.
 */
export async function findBusinessCategoryId(category: StoreCategory): Promise<BusinessCategoryLookup> {
  try {
    const match = (await fetchBusinessCategories()).find((c) => c.name === category);
    return match ? { status: "found", id: match.id } : { status: "missing" };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "failed" };
  }
}

/** 매장 대표사진 한 장을 올린다 (용도 STORE) */
export async function uploadStorePhoto(file: File): Promise<PhotoUploadResult> {
  try {
    return { status: "uploaded", imageUrl: await uploadStoreImage(file) };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "failed" };
  }
}

/** 가입 3단계 입력을 POST /auth/owner 본문으로 바꾼다. 빈 선택 값은 보내지 않는다 */
export function toOwnerRegistration(
  draft: OwnerSignupDraft,
  categoryId: number,
  images: { profileImageUrl: string; storeImageUrls: string[] },
): OwnerRegistrationRequest {
  const description = draft.description.trim();
  const storeAddress = draft.storeAddress.trim();
  return {
    name: draft.name.trim(),
    storeName: draft.storeName.trim(),
    ...(storeAddress && { storeAddress }),
    categoryId,
    businessNumber: draft.business.number,
    openedAt: toIsoDate(draft.business.openedAt),
    representativeName: draft.business.representative.trim(),
    ...(description && { description }),
    storeImageUrls: images.storeImageUrls,
    ...(images.profileImageUrl && { profileImageUrl: images.profileImageUrl }),
  };
}

/**
 * 사장님 가입을 저장한다. 성공하면 역할이 OWNER 로 바뀐 새 access token 을 저장한다
 * (refresh token 은 HTTP-only 쿠키로만 온다).
 */
export async function registerOwnerSignup(
  draft: OwnerSignupDraft,
  categoryId: number,
  images: { profileImageUrl: string; storeImageUrls: string[] },
): Promise<OwnerRegisterResult> {
  try {
    saveAccessToken(await registerOwner(toOwnerRegistration(draft, categoryId, images)));
    return "registered";
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return "unauthorized";
      switch (error.code) {
        case "USER_409_REGISTERED":
          return "alreadyRegistered";
        case "OWNER_409_BUSINESS_NUMBER":
          return "businessNumberTaken";
        case "CATEGORY_400":
          return "categoryInvalid";
      }
      // 원인이 분명하지 않은 400·409 는 5xx 의 「잠시 후 다시 시도」와 다른 문구로 나눈다 (ADR 0019)
      if (error.status === 400) return "invalidInput";
      if (error.status === 409) return "dataConflict";
    }
    return "error";
  }
}
