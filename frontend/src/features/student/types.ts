import type { Field } from "../../types/field";
import type { WorkKind } from "../../types/workKind";
import type { ApplicationPlan } from "../../types/workPlan";

/** 마감 단계. draft = 초안, final = 최종 */
export type DeadlineStage = "draft" | "final";

/** 사장님이 올린 가게 고민 (GET /explore/stores 의 concern). 학생이 제안을 쓸 때 참고하는 정보다 */
export interface StoreConcern {
  title: string;
  /** 자세한 설명. 없으면 null */
  description: string | null;
  /** 분야(특기 대분류) 이름. 고르지 않았으면 null */
  category: string | null;
  /** 올리거나 마지막으로 고친 시각 (+09:00) */
  updatedAt: string;
}

/**
 * GET /explore/stores 의 가게 하나 (사장님 프로필 하나 = 가게 하나).
 * 제안을 보낼 때 ownerProfileId 를 쓰고, 가게 하나만 조회하는 API 가 없어 이름·업종·주소·고민도 함께 넘긴다.
 */
export interface ExploreStore {
  ownerProfileId: number;
  name: string;
  /** 백엔드 업종 이름 (예: 음식점) */
  category: string;
  address: string;
  /** 사장님이 올린 가게 사진. 없으면 없다 */
  photo?: string | null;
  /** 해결되지 않은 가게 고민. 없으면 null */
  concern?: StoreConcern | null;
}

export interface WorkFile {
  name: string;
  /** 「24.1MB」 */
  size: string;
  /** 제출 화면에서 고른 파일 (올릴 때 쓴다) */
  file?: File;
}

/** 지원하기에서 쓰는 작업계획서 */
export type { ApplicationPlan };

/** 홈 「이런 제안은 어때요?」 예시. 누르면 제안 보내기를 이 내용으로 채워 시작한다 */
export interface ProposalExample {
  id: string;
  field: Field;
  task: string;
  /** 줄바꿈(\n)까지 그대로 */
  title: string;
  /** 제안 보내기 3/4 에 미리 채울 제목 */
  proposalTitle: string;
}

/** 정산 요약 3칸 (GET /settlements 의 summary) */
export interface SettlementSummary {
  /** 이번 달에 결제된 작업의 금액 합 */
  thisMonth: number;
  /** 정산 예정 (작업 중) */
  expected: number;
  /** 지금까지 정산된 금액 (착수 보상 포함) */
  settled: number;
}

/** 확인할 일 카드 (GET /me/home 의 todos). 가게 이름이 없으면 storeName 이 없다 */
export type StudentTodo =
  /** 사장님이 결제해 의뢰서가 온 내 제안 */
  | {
      type: "proposalAgreement";
      proposalId: number;
      title: string;
      /** 겹치지 않는 대분류 이름 */
      categories: string[];
      storeName?: string;
    }
  /** 초안 · 수정안을 만들 차례인 작업. stage · due 는 지금 지킬 마감 (초안 차례는 초안, 수정안 차례는 최종) */
  | {
      type: "drafting" | "revising";
      jobId: number;
      kind: WorkKind;
      title: string;
      categories: string[];
      storeName?: string;
      stage: DeadlineStage;
      due: string;
    };

/** 사장님이 확인 중 한 줄 (GET /me/home 의 checking). 결과물을 내고 수정 요청을 받지 않은 작업 */
export interface StudentCheckingItem {
  jobId: number;
  kind: WorkKind;
  title: string;
  storeName?: string;
  /** 마지막으로 낸 것이 수정안이면 true (「수정안 제출」) */
  revisionSubmitted: boolean;
  /** 마지막으로 낸 한국 날짜 "2026-10-07" */
  submittedOn?: string;
}

/** 기다리는 중 한 줄 (GET /me/home 의 waiting) */
export type StudentWaitingItem =
  /** 수락 대기 중인 보낸 제안 */
  | { type: "proposal"; proposalId: number; title: string; storeName?: string; likeCount: number }
  /** 사장님이 고르는 중인 지원 */
  | {
      type: "application";
      jobId: number;
      jobApplicationId: number;
      title: string;
      storeName?: string;
      draftDeadline?: string;
    };

/** 다른 학생들의 제안 공감하기 한 줄 (GET /me/home 의 peerProposals, 내 제안은 빠져 있다) */
export interface StudentPeerProposal {
  proposalId: number;
  title: string;
  studentName?: string;
  storeName: string;
  /** 수락 대기(PENDING) 제안만 공감할 수 있다 */
  status?: string;
  likeCount: number;
  likedByMe: boolean;
}

/** 끝난 일 한 줄 (GET /me/home 의 done). 정산까지 끝난 작업 */
export interface StudentDoneItem {
  jobId: number;
  kind: WorkKind;
  title: string;
  storeName?: string;
  /** 정산된 날 "2026-10-08" */
  completedOn?: string;
}

/**
 * 학생 홈 한 화면 분량 (GET /me/home). 목록이 null 이면 그 섹션만 불러오지 못한 것이고
 * (홈을 다시 불러와 재시도), 빈 목록이면 불러왔는데 없는 것이다.
 */
export interface StudentHome {
  /**
   * 이력(제안 · 지원 · 작업 · 정산)이 하나도 없으면 할 일 대신 사용법 안내.
   * 서버가 알 수 없다고(null) 하면 보이는 목록에 항목이 있을 때만 false, 아니면 undefined (모름)
   */
  firstVisit: boolean | undefined;
  /** 의뢰서가 온 내 제안 → 초안 · 수정안 차례 (마감이 빠른 것부터) */
  todos: StudentTodo[] | null;
  checking: StudentCheckingItem[] | null;
  /** 수락 대기 중인 보낸 제안 → 고르는 중인 지원 */
  waiting: StudentWaitingItem[] | null;
  /** 공감 많은 다른 학생 제안 2개. 못 불러왔으면 빈 목록 (섹션째 숨긴다) */
  peerProposals: StudentPeerProposal[];
  /** 최근 끝난 것부터 */
  done: StudentDoneItem[] | null;
}
