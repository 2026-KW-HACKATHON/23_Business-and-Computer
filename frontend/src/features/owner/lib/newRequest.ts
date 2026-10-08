import { ApiError } from "../../../api/client";
import { IMAGE_UPLOAD_EXTENSIONS, MAX_IMAGE_UPLOAD_BYTES, uploadImage } from "../../../api/media";
import { todayIsoDate } from "../../../lib/date";
import { findSpecialtyByName, implicitSpecialty } from "../../specialty";
import type { SpecialtyCategory } from "../../specialty";
import { createJob } from "../api/jobApi";
import type { JobCreateRequest } from "../api/jobApi";
import type { DueDates, PickedTask, RequestContent } from "../types";

/**
 * 의뢰 등록 1/3 → 2/3 → 3/3 사이에 router state 로 넘기는 값.
 * 다음 단계로 가기 전에 지금 화면 기록에도 저장해 두어서 ← 로 돌아와도 고친 값이 남는다.
 */
export interface NewRequestState {
  /** 홈 예시 카드로 들어왔을 때 그 예시 id */
  exampleId?: string;
  /** 1/3 에서 펼친 대분류 id (← 로 돌아왔을 때 그대로 보이게) */
  categoryIds: number[];
  picked: PickedTask[];
  /** 2/3 에서 적은 내용 */
  content?: RequestContent;
}

/** router state 를 읽는다. 주소로 바로 들어와 값이 없으면 undefined */
export function readNewRequestState(state: unknown): NewRequestState | undefined {
  if (!state || typeof state !== "object") return undefined;
  const value = state as Partial<NewRequestState>;
  if (!Array.isArray(value.categoryIds) || !Array.isArray(value.picked)) return undefined;
  return value as NewRequestState;
}

/** 의뢰서 「할 일」 줄 */
export function taskSummary({ picked }: NewRequestState): string {
  return picked.map((p) => p.name).join(", ");
}

/** 고른 일의 대분류 이름 (겹치지 않게, 고른 순서대로) */
export function requestCategoryNames({ picked }: NewRequestState): string[] {
  return [...new Set(picked.map((p) => p.categoryName))];
}

/** 두 마감일을 다 고르고 최종 마감이 초안 마감보다 앞서지 않는지 */
export function dueDatesReady({ draftDue, finalDue }: DueDates): boolean {
  return draftDue !== "" && finalDue !== "" && draftDue >= todayIsoDate() && finalDue >= draftDue;
}

/** 의뢰 참고 사진은 최대 4장 (POST /jobs 가 받는 수) */
export const MAX_REQUEST_PHOTOS = 4;

/** 사진 고르기 창에서 보여 줄 형식 (업로드 API 와 같은 jpg · png · webp) */
export const REQUEST_PHOTO_ACCEPT = Object.keys(IMAGE_UPLOAD_EXTENSIONS).join(",");

/**
 * 고른 사진을 지금 목록 뒤에 붙인다. 형식이 다르거나 10MB 를 넘는 사진, 4장을 넘는 사진은
 * 빼고, 뺀 이유를 notice 로 돌려준다.
 */
export function addRequestPhotos(
  current: File[],
  picked: File[],
): { photos: File[]; notice?: string } {
  let notice: string | undefined;
  const ok = picked.filter((file) => {
    if (!(file.type in IMAGE_UPLOAD_EXTENSIONS)) {
      notice = "JPG, PNG, WEBP 사진만 올릴 수 있어요";
      return false;
    }
    if (file.size > MAX_IMAGE_UPLOAD_BYTES) {
      notice = "10MB 이하 사진만 올릴 수 있어요";
      return false;
    }
    return true;
  });
  const photos = [...current, ...ok];
  if (photos.length > MAX_REQUEST_PHOTOS) {
    notice = `사진은 ${MAX_REQUEST_PHOTOS}장까지 올릴 수 있어요`;
  }
  return { photos: photos.slice(0, MAX_REQUEST_PHOTOS), notice };
}

/** 1.8MB · 240KB */
export function photoSizeText(bytes: number): string {
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)}MB`
    : `${Math.max(1, Math.round(bytes / 1024))}KB`;
}

/** 의뢰 등록 1/3 에서 고른 분야 · 일 */
export type RequestChoice = Pick<NewRequestState, "categoryIds" | "picked">;

/**
 * 「우리 가게에도 비슷한 의뢰 만들기」: 의뢰서 · 제안서의 대분류(GET /jobs/{id} · GET /proposals/{id} 의
 * specialtyCategories)가 골라진 의뢰 등록 1/3. withTasks 면 그 안의 특기도 골라 둔다.
 */
export function similarRequestState(
  categories: SpecialtyCategory[],
  { withTasks }: { withTasks: boolean },
): NewRequestState {
  return {
    categoryIds: categories.map((category) => category.id),
    picked: withTasks
      ? categories.flatMap((category) =>
          category.specialties.map((specialty) => ({
            specialtyId: specialty.id,
            name: specialty.name,
            categoryId: category.id,
            categoryName: category.name,
          })),
        )
      : [],
  };
}

/**
 * 홈 예시의 분야 · 일 이름을 서버 목록에서 찾아 고른 값으로. 이름이 서버에 없으면 아무것도 고르지 않는다
 * (학생 제안 예시와 같은 규칙).
 */
export function exampleRequestChoice(
  categories: SpecialtyCategory[],
  example: { field: string; task: string },
): RequestChoice {
  const found = findSpecialtyByName(categories, example.field, example.task);
  if (!found) return { categoryIds: [], picked: [] };
  return {
    categoryIds: [found.category.id],
    picked: [
      {
        specialtyId: found.specialty.id,
        name: found.specialty.name,
        categoryId: found.category.id,
        categoryName: found.category.name,
      },
    ],
  };
}

/**
 * 들고 온 분야 · 일을 지금 서버 목록에 맞춘다. 목록에 없는 분류 · 특기는 빼고, 「기타」처럼 카드로 정해지는
 * 분류는 그 특기를 골라 둔다.
 */
export function fitRequestChoice(categories: SpecialtyCategory[], choice: RequestChoice): RequestChoice {
  const shown = categories.filter((category) => choice.categoryIds.includes(category.id));
  const picked: PickedTask[] = [];
  const add = (category: SpecialtyCategory, specialty: { id: number; name: string }) => {
    if (picked.some((p) => p.specialtyId === specialty.id)) return;
    picked.push({
      specialtyId: specialty.id,
      name: specialty.name,
      categoryId: category.id,
      categoryName: category.name,
    });
  };
  // 고른 순서를 지킨다
  for (const task of choice.picked) {
    const category = shown.find((c) => c.id === task.categoryId);
    const specialty = category?.specialties.find((s) => s.id === task.specialtyId);
    if (category && specialty) add(category, specialty);
  }
  for (const category of shown) {
    const only = implicitSpecialty(category);
    if (only) add(category, only);
  }
  return { categoryIds: shown.map((category) => category.id), picked };
}

/** 3/3 에서 보낼 POST /jobs 본문 */
export function toJobCreateRequest(
  content: RequestContent,
  picked: PickedTask[],
  referenceImageUrls: string[],
): JobCreateRequest {
  return {
    specialtyIds: [...new Set(picked.map((p) => p.specialtyId))],
    title: content.title.trim(),
    description: content.description.trim(),
    budget: content.budget,
    draftDeadline: content.draftDue,
    finalDeadline: content.finalDue,
    revisionCount: content.revisions,
    referenceImageUrls,
  };
}

/** 참고 사진 한 장 업로드 결과 */
export type RequestPhotoUploadResult =
  | { status: "uploaded"; imageUrl: string }
  | { status: "unauthorized" }
  | { status: "failed" };

/** 참고 사진 한 장을 공용 uploadImage(용도 JOB)로 올린다. 형식 · 크기는 고를 때 이미 막았다 */
export async function uploadRequestPhoto(file: File): Promise<RequestPhotoUploadResult> {
  try {
    return { status: "uploaded", imageUrl: await uploadImage(file, "JOB") };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "unauthorized" };
    return { status: "failed" };
  }
}

/** POST /jobs 결과 */
export type RequestSendResult =
  | { status: "created" }
  | {
      status:
        | "unauthorized"
        /** 403 OWNER_403 — 사장님 프로필이 없음 */
        | "notOwner"
        /** 400 SPECIALTY_400 / SPECIALTY_400_DUPLICATE — 특기 목록이 바뀜 */
        | "specialtyInvalid"
        /** 400 JOB_400_IMAGE_URL / 409 JOB_409_IMAGE_NOT_UPLOADED — 사진을 다시 올려야 함 */
        | "photoInvalid"
        /** 그 밖의 400. 같은 값으로 다시 보내도 실패한다 */
        | "invalidInput"
        /** 그 밖의 409 */
        | "dataConflict"
        /** 5xx · 네트워크 */
        | "error";
    };

/** 의뢰를 등록하고 결과를 화면이 쓰는 값으로 바꾼다 */
export async function sendJobCreate(request: JobCreateRequest): Promise<RequestSendResult> {
  try {
    await createJob(request);
    return { status: "created" };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 401) return { status: "unauthorized" };
      switch (error.code) {
        case "OWNER_403":
          return { status: "notOwner" };
        case "SPECIALTY_400":
        case "SPECIALTY_400_DUPLICATE":
          return { status: "specialtyInvalid" };
        case "JOB_400_IMAGE_URL":
        case "JOB_409_IMAGE_NOT_UPLOADED":
          return { status: "photoInvalid" };
      }
      if (error.status === 400) return { status: "invalidInput" };
      if (error.status === 409) return { status: "dataConflict" };
    }
    return { status: "error" };
  }
}
