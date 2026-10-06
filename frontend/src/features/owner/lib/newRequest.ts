import { ApiError } from "../../../api/client";
import { IMAGE_UPLOAD_EXTENSIONS, MAX_IMAGE_UPLOAD_BYTES, uploadImage } from "../../../api/media";
import { todayIsoDate } from "../../../lib/date";
import type { Field } from "../../../types/field";
import { SPECIALTY_BADGES } from "../../../types/specialty";
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
  fields: Field[];
  picked: PickedTask[];
  /** 2/3 에서 적은 내용 */
  content?: RequestContent;
}

/** router state 를 읽는다. 주소로 바로 들어와 값이 없으면 undefined */
export function readNewRequestState(state: unknown): NewRequestState | undefined {
  if (!state || typeof state !== "object") return undefined;
  const value = state as Partial<NewRequestState>;
  if (!Array.isArray(value.fields) || !Array.isArray(value.picked)) return undefined;
  return value as NewRequestState;
}

/** 의뢰서 「할 일」 줄. 고른 일이 없으면 (기타만 고른 경우) 분야 이름 */
export function taskSummary({ fields, picked }: NewRequestState): string {
  return picked.length > 0 ? picked.map((p) => p.task).join(", ") : fields.join(", ");
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

/** 「우리 가게에도 비슷한 의뢰 만들기」: 같은 분야 · 같은 일이 골라진 의뢰 등록 1/3 */
export function similarRequestState(field: Field, tasks: string[] = []): NewRequestState {
  const known = SPECIALTY_BADGES.find((group) => group.field === field)?.badges ?? [];
  return {
    fields: [field],
    picked: tasks.filter((task) => known.includes(task)).map((task) => ({ field, task })),
  };
}

/**
 * 고른 분야 · 할 일을 서버 특기 id 로 바꾼다. 할 일은 분야 이름 + 할 일 이름으로 찾고, 할 일을 고르지
 * 않은 분야(「기타」)는 그 분야에 하나뿐인 특기를 쓴다. 하나라도 서버에 없으면 undefined (목록이 바뀜).
 */
export function requestSpecialtyIds(
  { fields, picked }: NewRequestState,
  categories: SpecialtyCategory[],
): number[] | undefined {
  const ids: number[] = [];
  for (const { field, task } of picked) {
    const found = findSpecialtyByName(categories, field, task);
    if (!found) return undefined;
    ids.push(found.specialty.id);
  }
  for (const field of fields.filter((f) => !picked.some((p) => p.field === f))) {
    const category = categories.find((c) => c.name === field);
    const only = category && implicitSpecialty(category);
    if (!only) return undefined;
    ids.push(only.id);
  }
  return ids.length > 0 ? [...new Set(ids)] : undefined;
}

/** 3/3 에서 보낼 POST /jobs 본문 */
export function toJobCreateRequest(
  content: RequestContent,
  specialtyIds: number[],
  referenceImageUrls: string[],
): JobCreateRequest {
  return {
    specialtyIds,
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
