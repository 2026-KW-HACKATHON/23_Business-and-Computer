import { apiFetch } from "../../../api/client";
import type { ApiResponse } from "../../../api/client";
import { getAccessToken } from "../../auth";
import type { BusinessInfo } from "../types";

interface OwnerBusinessVerificationResponse {
  verified: boolean;
}

/** YYYYMMDD → YYYY-MM-DD (백엔드 openedAt 은 LocalDate ISO 형식만 받는다) */
function toIsoDate(yyyymmdd: string): string {
  return `${yyyymmdd.slice(0, 4)}-${yyyymmdd.slice(4, 6)}-${yyyymmdd.slice(6, 8)}`;
}

/**
 * POST /auth/owner-verification/business — 국세청 진위 확인.
 * 정보가 틀리면 200 에 verified:false, 그 밖의 실패는 ApiError 로 던진다.
 */
export async function verifyOwnerBusiness(info: BusinessInfo): Promise<boolean> {
  const token = getAccessToken();
  const response = await apiFetch<ApiResponse<OwnerBusinessVerificationResponse>>(
    "/auth/owner-verification/business",
    {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: JSON.stringify({
        representativeName: info.representative.trim(),
        openedAt: toIsoDate(info.openedAt),
        businessNumber: info.number,
      }),
    },
  );
  return response.data?.verified === true;
}
