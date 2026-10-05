import { apiData } from "../../../api/client";

/** 탐색 카드 종류 필터. ALL = 제안과 의뢰 */
export type ExploreType = "ALL" | "PROPOSAL" | "JOB";

/** 최신순 · 오래된순 · 공감 많은 순. LIKES 는 제안(PROPOSAL)에만 쓸 수 있다 */
export type ExploreSort = "LATEST" | "OLDEST" | "LIKES";

/** 의뢰 상태. 탐색에는 취소된 의뢰가 오지 않는다 */
export type JobStatus = "OPEN" | "AWAITING_START" | "MATCHED" | "CLOSED" | "CANCELLED";

/** 의뢰 진행 단계 (서버가 상태와 최신 제출물에서 계산한다) */
export type JobProgressStage =
  | "REQUESTED"
  | "AWAITING_START"
  | "STARTED"
  | "DRAFT"
  | "REVISION"
  | "COMPLETED"
  | "CANCELLED";

/** 대분류 + 그 안의 특기 */
export interface ExploreSpecialtyCategory {
  id: number;
  name: string;
  specialties: { id: number; name: string }[];
}

/** GET /explore 의 제안 카드 */
export interface ExploreProposalCard {
  type: "PROPOSAL";
  proposalId: number;
  title: string;
  storeName: string;
  likeCount: number;
  specialtyCategories: ExploreSpecialtyCategory[];
  /** 제안한 학생 이름. 서버가 주면 「○○ 학생 → 가게」로 보인다 */
  studentName?: string | null;
  /** 내가 공감했는지. 서버가 주면 하트가 채워진다 */
  likedByMe?: boolean | null;
  /** 제안 상태. 서버가 주면 「수락 대기 중」 같은 상태 줄이 보인다 */
  status?: "PENDING" | "AWAITING_START" | "ACCEPTED" | "REJECTED" | null;
  /** 이렇게 바꿔 드릴게요. 서버가 주면 카드에 두 줄 미리보기가 보인다 */
  proposedSolution?: string | null;
}

/** GET /explore 의 의뢰 카드 */
export interface ExploreJobCard {
  type: "JOB";
  jobId: number;
  storeName: string;
  title: string;
  progressStage: JobProgressStage;
  status: JobStatus;
  /** "2026-10-12" */
  draftDeadline: string;
  finalDeadline: string;
  specialtyCategories: ExploreSpecialtyCategory[];
  /** 작업비(원). 서버가 주면 카드에 「예산」 줄이 보인다 */
  budget?: number | null;
  /** 내가 이미 지원했는지. 서버가 주면 「지원했어요」로 바뀐다 */
  applied?: boolean | null;
}

export type ExploreItem = ExploreProposalCard | ExploreJobCard;

/** 한 쪽. 다음 쪽이 없으면 nextCursor 는 null */
export interface ExplorePage {
  items: ExploreItem[];
  nextCursor: string | null;
  hasNext: boolean;
}

export interface ExploreQuery {
  type: ExploreType;
  /** 대분류 id. 없으면 모든 분야 */
  categoryId?: number;
  sort: ExploreSort;
  /** 앞 쪽의 nextCursor. 같은 type · categoryId · sort 로만 이어 부를 수 있다 */
  cursor?: string;
  /** 한 쪽의 카드 수 (서버 허용 1~100). 없으면 20 */
  size?: number;
}

/** 한 번에 받는 카드 수 기본값 */
const EXPLORE_PAGE_SIZE = 20;

/** GET /explore — 같은 데모 세션의 제안 · 의뢰를 커서로 한 쪽씩 */
export async function fetchExplore({
  type,
  categoryId,
  sort,
  cursor,
  size = EXPLORE_PAGE_SIZE,
}: ExploreQuery): Promise<ExplorePage> {
  const params = new URLSearchParams({ type, sort, size: String(size) });
  if (categoryId !== undefined) params.set("specialtyCategoryId", String(categoryId));
  if (cursor) params.set("cursor", cursor);
  const data = await apiData<Partial<ExplorePage> | undefined>(`/explore?${params.toString()}`);
  const nextCursor = data?.nextCursor ?? null;
  return {
    items: data?.items ?? [],
    nextCursor,
    hasNext: data?.hasNext === true && nextCursor !== null,
  };
}
