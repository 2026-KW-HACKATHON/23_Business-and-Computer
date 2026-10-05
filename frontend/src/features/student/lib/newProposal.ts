import { ApiError } from "../../../api/client";
import { IMAGE_UPLOAD_EXTENSIONS, MAX_IMAGE_UPLOAD_BYTES, uploadImage } from "../../../api/media";
import { createProposal } from "../api/proposalApi";
import type { ProposalCreateRequest } from "../api/proposalApi";
import type { ExploreStore } from "../types";

/** 제안 보내기 2/4 에서 고른 일 하나 (GET /specialties 의 특기) */
export interface PickedTask {
  specialtyId: number;
  /** 특기 이름 (예: 메뉴판·가격표 디자인) */
  name: string;
  /** 대분류 id · 이름 (예: 디자인). 4/4 뱃지에 쓴다 */
  categoryId: number;
  categoryName: string;
}

/** 제안 보내기 3/4 에서 적는 내용 */
export interface ProposalContent {
  title: string;
  /** 손님 눈으로 본 문제 */
  problem: string;
  /** 이렇게 바꿔 드릴게요 */
  solution: string;
  /** 작업계획서 */
  plan: string;
  /** 원. 아직 안 적었으면 0 */
  wishBudget: number;
  /** 수락된 날부터 초안 · 최종까지 걸리는 날 (0 이면 아직 안 적음) */
  draftDays: number;
  finalDays: number;
  /** 참고 사진. 4/4 「제안 보내기」 때 올린다 (router state 에 File 그대로 둔다) */
  photos: File[];
}

/**
 * 제안 보내기 1/4 → 2/4 → 3/4 → 4/4 사이에 router state 로 넘기는 값.
 * 다음 단계로 가기 전에 지금 화면 기록에도 저장해 두어서 ← 로 돌아와도 고친 값이 남는다.
 */
export interface NewProposalState {
  /** 홈 예시 카드로 들어왔을 때 그 예시 id */
  exampleId?: string;
  /** 고른 가게. 가게 하나만 조회하는 API 가 없어 이름·업종·주소까지 들고 다닌다 */
  store?: ExploreStore;
  /** 2/4 에서 펼친 대분류 id (← 로 돌아왔을 때 그대로 보이게) */
  categoryIds: number[];
  picked: PickedTask[];
  content?: ProposalContent;
}

/** 참고 사진 장 수 · 형식 · 크기 (형식·크기는 공용 api/media 의 백엔드 기준) */
export const MAX_PROPOSAL_PHOTOS = 5;
export const PROPOSAL_PHOTO_ACCEPT = Object.keys(IMAGE_UPLOAD_EXTENSIONS).join(",");

/** 고른 사진을 올릴 수 있는지. accept 는 우회될 수 있어 형식도 다시 본다 */
export function checkProposalPhoto(file: File): "ok" | "type" | "size" {
  if (!(file.type in IMAGE_UPLOAD_EXTENSIONS)) return "type";
  if (file.size > MAX_IMAGE_UPLOAD_BYTES) return "size";
  return "ok";
}

function isExploreStore(value: unknown): value is ExploreStore {
  return (
    typeof value === "object" &&
    value !== null &&
    typeof (value as ExploreStore).ownerProfileId === "number"
  );
}

/** router state 를 읽는다. 주소로 바로 들어와 값이 없으면 undefined */
export function readNewProposalState(state: unknown): NewProposalState | undefined {
  if (!state || typeof state !== "object") return undefined;
  const value = state as Partial<NewProposalState>;
  const store = isExploreStore(value.store) ? value.store : undefined;
  if (!Array.isArray(value.categoryIds) || !Array.isArray(value.picked)) {
    // 가게 탐색 「제안하기」 · 홈 예시는 고른 일 없이 들어온다
    if (store || typeof value.exampleId === "string") {
      return { exampleId: value.exampleId, store, categoryIds: [], picked: [] };
    }
    return undefined;
  }
  return { ...(value as NewProposalState), store };
}

/**
 * 「메뉴판·가격표 디자인, 영어 번역」. 「기타」처럼 분류와 이름이 같은 일은 무엇을 할지
 * 3/4 내용에 적으므로 「기타 (아래 내용 참고)」로 보인다.
 */
export function proposalTaskSummary({ picked }: NewProposalState): string {
  return picked
    .map((p) => (p.name === p.categoryName ? `${p.name} (아래 내용 참고)` : p.name))
    .join(", ");
}

/** 고른 일의 대분류 이름 (겹치지 않게, 고른 순서대로) */
export function proposalCategoryNames({ picked }: NewProposalState): string[] {
  return [...new Set(picked.map((p) => p.categoryName))];
}

/** 「초안 2일 · 최종 4일」 */
export function expectedDaysText(draftDays: number, finalDays: number): string {
  return `초안 ${draftDays}일 · 최종 ${finalDays}일`;
}

/** 4/4 state → POST /proposals 본문 */
export function toProposalRequest(
  store: ExploreStore,
  picked: PickedTask[],
  content: ProposalContent,
  referenceImageUrls: string[],
): ProposalCreateRequest {
  return {
    ownerProfileId: store.ownerProfileId,
    specialtyIds: [...new Set(picked.map((p) => p.specialtyId))],
    title: content.title.trim(),
    customerProblem: content.problem.trim(),
    proposedSolution: content.solution.trim(),
    workPlan: content.plan.trim(),
    proposedFee: content.wishBudget,
    draftDays: content.draftDays,
    finalDays: content.finalDays,
    referenceImageUrls,
  };
}

/** 참고 사진 한 장 업로드 결과 */
export type ProposalPhotoUploadResult =
  | { status: "uploaded"; imageUrl: string }
  | { status: "unauthorized" }
  | { status: "failed" };

/**
 * 참고 사진 한 장을 공용 uploadImage(용도 PROPOSAL)로 올린다.
 * 401 은 apiData 가 /refresh 로 한 번 다시 시도한 뒤에도 실패한 경우다. 그 밖에는 모두 실패로 본다
 * (형식·크기는 고를 때 이미 막았다).
 */
export async function uploadProposalPhoto(file: File): Promise<ProposalPhotoUploadResult> {
  try {
    return { status: "uploaded", imageUrl: await uploadImage(file, "PROPOSAL") };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "failed" };
  }
}

/** POST /proposals 결과. sent 면 만든 제안 id 가 있다 */
export type ProposalSendResult =
  | { status: "sent"; proposalId: number }
  | {
      status:
        | "unauthorized"
        /** 403 PROPOSAL_403_STUDENT — 학생이 아님 */
        | "notStudent"
        /** 404 OWNER_404 — 가게가 사라졌음 */
        | "storeGone"
        /** 400 SPECIALTY_400 / SPECIALTY_400_DUPLICATE — 특기 목록이 바뀜 */
        | "specialtyInvalid"
        /** 400 PROPOSAL_400_IMAGE_URL / 409 PROPOSAL_409_IMAGE_NOT_UPLOADED — 사진을 다시 올려야 함 */
        | "photoInvalid"
        /** 그 밖의 400. 같은 값으로 다시 보내도 실패한다 */
        | "invalidInput"
        /** 그 밖의 409 (COMMON_409). 서버 데이터 문제일 수 있어 다시 시도하면 될 수도 있다 */
        | "dataConflict"
        /** 5xx (MEDIA_UPLOAD_502 포함) · 네트워크 */
        | "error";
    };

/** 제안을 보내고 결과를 화면이 쓰는 값으로 바꾼다 */
export async function sendProposalRequest(request: ProposalCreateRequest): Promise<ProposalSendResult> {
  try {
    return { status: "sent", proposalId: await createProposal(request) };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      switch (error.code) {
        case "PROPOSAL_403_STUDENT":
          return { status: "notStudent" };
        case "OWNER_404":
          return { status: "storeGone" };
        case "SPECIALTY_400":
        case "SPECIALTY_400_DUPLICATE":
          return { status: "specialtyInvalid" };
        case "PROPOSAL_400_IMAGE_URL":
        case "PROPOSAL_409_IMAGE_NOT_UPLOADED":
          return { status: "photoInvalid" };
      }
      if (error.status === 400) return { status: "invalidInput" };
      if (error.status === 409) return { status: "dataConflict" };
    }
    return { status: "error" };
  }
}
