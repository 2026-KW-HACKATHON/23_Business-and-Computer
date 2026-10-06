import { apiData } from "../../../api/client";

/** POST /jobs 본문 (JobCreateRequest). 등록한 의뢰는 고칠 수 없다 */
export interface JobCreateRequest {
  /** 고른 할 일의 서버 특기 id (1개 이상, 겹치지 않게) */
  specialtyIds: number[];
  /** 255자까지 */
  title: string;
  description: string;
  /** 작업비(원), 1 이상 */
  budget: number;
  /** "2026-10-12". 오늘 이후, 초안 마감 ≤ 최종 마감 */
  draftDeadline: string;
  finalDeadline: string;
  revisionCount: number;
  /** uploadImage(file, "JOB") 로 받은 주소 (최대 4장, 겹치지 않게) */
  referenceImageUrls: string[];
}

/** POST /jobs — 의뢰 등록. 답에는 데이터가 없다 */
export async function createJob(request: JobCreateRequest): Promise<void> {
  await apiData<unknown>("/jobs", { method: "POST", body: JSON.stringify(request) });
}
