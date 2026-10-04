/**
 * 의뢰 지원서. 학생이 「지원하기」에서 적고, 사장님은 지원자 목록 · 이 학생에게 맡기기 ·
 * 채팅방 「작업계획서 보기」에서 본다. 마감은 사장님이 의뢰에서 정해서 따로 적지 않는다.
 */
export interface ApplicationPlan {
  /** 한 줄 요약 (지원자 카드에 가장 먼저 보인다) */
  summary: string;
  /** 작업계획서 (어떻게 만들고 검수할지) */
  method: string;
  /** 결과물 */
  deliverable: string;
}

/**
 * 작업의 작업계획서. 의뢰에 지원해 시작한 작업은 지원서,
 * 제안으로 시작한 작업은 제안서의 작업계획서 글(줄마다 한 문단)이다.
 */
export type WorkPlanContent = ApplicationPlan | string;
