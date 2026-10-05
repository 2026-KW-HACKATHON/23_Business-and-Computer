import type { RequestExample } from "../types";
import { day } from "../../../lib/sampleTime";

/**
 * 홈 「이런 의뢰는 어때요?」 예시 (피그마 「사장님 홈」 · B-2).
 * 누르면 의뢰 등록 1/3 · 2/3 을 이 내용으로 채워 시작한다. 마감일은 오늘 기준.
 */
export const SAMPLE_REQUEST_EXAMPLES: RequestExample[] = [
    {
      id: "example-review",
      field: "분석",
      task: "리뷰 분석",
      title: "손님 리뷰에서\n아쉬운 점을 찾아 드려요",
      content: {
        title: "손님 리뷰 분석",
        description:
          "네이버·카카오 리뷰에서 손님들이 아쉬워하는 점을 정리해 주세요. 무엇부터 고치면 좋을지도 알려 주면 좋겠어요.",
        budget: 40_000,
        draftDue: day(8),
        finalDue: day(14),
        revisions: 1,
      },
    },
    {
      id: "example-booking",
      field: "개발·IT",
      task: "온라인 예약·주문서",
      title: "전화 대신 받는\n예약서를 만들어 드려요",
      content: {
        title: "온라인 예약서 만들기",
        description:
          "점심·단체 예약을 전화로만 받아서 바쁠 때 놓쳐요. 손님이 휴대폰으로 날짜와 인원을 적는 예약서를 만들어 주세요.",
        budget: 50_000,
        draftDue: day(10),
        finalDue: day(16),
        revisions: 1,
      },
    },
    {
      id: "example-coupon",
      field: "디자인",
      task: "쿠폰·스티커·명함 디자인",
      title: "단골 쿠폰·도장카드를\n만들어 드려요",
      content: {
        title: "단골 쿠폰·도장카드 디자인",
        description:
          "10번 오면 음료 한 잔을 주는 도장카드를 만들고 싶어요. 가게 분위기에 맞게 디자인하고 인쇄용 파일로 주세요.",
        budget: 30_000,
        draftDue: day(7),
        finalDue: day(10),
        revisions: 1,
      },
    },
  ];

/** 첫 활동 여부. 백엔드가 알려 줄 때까지 예시 계정은 할 일이 있는 계정이다 */
export const SAMPLE_FIRST_VISIT = false;
