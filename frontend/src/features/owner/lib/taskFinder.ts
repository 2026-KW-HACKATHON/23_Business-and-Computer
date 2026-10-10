import { day } from "../../../lib/sampleTime";
import type { RequestExample } from "../types";
import { SAMPLE_REQUEST_EXAMPLES } from "./sampleHome";

/**
 * 「맡길 일 찾기」 (피그마 「I. 맡길 일 찾기」 · 「질문 12개 · 추천 연결」 표, ADR 0067).
 * 질문마다 답이 두 개이고, 그중 `need` 번째 답을 고르면 그 질문의 일을 추천한다.
 * 추천 카드를 누르면 홈 「이런 의뢰는 어때요?」 예시처럼 의뢰 등록을 그 예시 내용으로 채워 시작한다.
 */
export interface TaskFinderQuestion {
  question: string;
  answers: readonly [string, string];
  /** 이 답(0 · 1)을 고르면 추천 */
  need: 0 | 1;
  /** 추천 카드 제목 */
  cardTitle: string;
  /** 추천 카드의 이유 한 줄 */
  reason: string;
  /** 의뢰 등록을 채울 예시 (findRequestExample 로 찾는다) */
  exampleId: string;
}

/** 맡길 일 찾기 예시. 홈 예시와 같은 일(리뷰 분석 · 예약서 · 도장카드)은 홈 예시를 그대로 쓴다 */
export const TASK_FINDER_EXAMPLES: RequestExample[] = [
  {
    id: "finder-menu",
    field: "디자인",
    task: "메뉴판·가격표 디자인",
    title: "새 메뉴판·가격표를 만들어 드려요",
    content: {
      title: "메뉴판·가격표 새로 만들기",
      description:
        "메뉴판(가격표)을 바꾼 지 오래돼서 지금 파는 메뉴와 가격으로 새로 만들고 싶어요. 1장으로 디자인하고 인쇄용 파일로 주세요.",
      budget: 40_000,
      draftDue: day(7),
      finalDue: day(12),
      revisions: 1,
      photos: [],
    },
  },
  {
    id: "finder-photo",
    field: "홍보",
    task: "음식·매장 사진",
    title: "가게와 상품 사진을 새로 찍어 드려요",
    content: {
      title: "가게·상품 사진 촬영",
      description:
        "네이버 지도에 올릴 가게와 상품 사진을 새로 찍어 주세요. 가게에 1시간쯤 와서 찍고, 보정한 사진 10장을 주세요.",
      budget: 30_000,
      draftDue: day(5),
      finalDue: day(9),
      revisions: 1,
      photos: [],
    },
  },
  {
    id: "finder-sns",
    field: "홍보",
    task: "SNS 게시물",
    title: "가게 SNS에 올릴 글을 만들어 드려요",
    content: {
      title: "가게 SNS 게시물 만들기",
      description: "가게 SNS에 올릴 게시물 3개를 만들어 주세요. 사진과 짧은 글을 함께 주시면 바로 올릴게요.",
      budget: 30_000,
      draftDue: day(6),
      finalDue: day(10),
      revisions: 1,
      photos: [],
    },
  },
  {
    id: "finder-sign",
    field: "디자인",
    task: "간판·현수막 시안",
    title: "새 간판·현수막 시안을 그려 드려요",
    content: {
      title: "간판·현수막 새 시안",
      description:
        "간판(현수막)이 멀리서 잘 안 보여서 바꾸고 싶어요. 눈에 잘 띄는 시안 1개를 그려 주세요. 제작은 따로 맡길게요.",
      budget: 40_000,
      draftDue: day(7),
      finalDue: day(12),
      revisions: 1,
      photos: [],
    },
  },
  {
    id: "finder-intro",
    field: "글쓰기·번역",
    task: "소개·공지 글쓰기",
    title: "가게 소개 글을 써 드려요",
    content: {
      title: "가게 소개 글 쓰기",
      description:
        "네이버 지도 같은 곳에 올릴 가게 소개 글을 써 주세요. 긴 소개 글 1개와 짧은 버전 1개가 필요해요.",
      budget: 20_000,
      draftDue: day(4),
      finalDue: day(7),
      revisions: 1,
      photos: [],
    },
  },
  {
    id: "finder-english",
    field: "글쓰기·번역",
    task: "영어 번역",
    title: "메뉴·가게 안내를 영어로 옮겨 드려요",
    content: {
      title: "메뉴·가게 안내 영어 번역",
      description: "외국인 손님이 자주 와서 메뉴와 가게 안내를 영어로 옮기고 싶어요. A4 1장 분량이에요.",
      budget: 20_000,
      draftDue: day(4),
      finalDue: day(7),
      revisions: 1,
      photos: [],
    },
  },
  {
    id: "finder-flyer",
    field: "디자인",
    task: "전단지·포스터 디자인",
    title: "새 메뉴·상품, 할인 전단지를 만들어 드려요",
    content: {
      title: "새 메뉴·상품, 할인 전단지",
      description:
        "새 메뉴(상품)와 할인 소식을 알릴 전단지를 만들어 주세요. A4 한 면 디자인이고 인쇄용 파일로 주세요.",
      budget: 40_000,
      draftDue: day(7),
      finalDue: day(12),
      revisions: 1,
      photos: [],
    },
  },
  {
    id: "finder-video",
    field: "홍보",
    task: "영상 제작 및 편집",
    title: "가게를 알리는 짧은 영상을 만들어 드려요",
    content: {
      title: "가게 홍보 짧은 영상",
      description: "SNS에 올릴 가게 소개 영상을 만들어 주세요. 30초 안팎으로, 가게에 와서 찍고 편집까지 해 주세요.",
      budget: 50_000,
      draftDue: day(8),
      finalDue: day(14),
      revisions: 1,
      photos: [],
    },
  },
  {
    id: "finder-event",
    field: "홍보",
    task: "홍보·이벤트 기획",
    title: "손님을 끄는 홍보·이벤트를 짜 드려요",
    content: {
      title: "손님 모으는 홍보·이벤트 기획",
      description:
        "손님이 뜸한 시간에 손님을 모을 이벤트를 짜 주세요. 진행 방법과 홍보 문구를 담은 기획안 1개가 필요해요.",
      budget: 30_000,
      draftDue: day(6),
      finalDue: day(10),
      revisions: 1,
      photos: [],
    },
  },
];

export const TASK_FINDER_QUESTIONS: readonly TaskFinderQuestion[] = [
  {
    question: "메뉴판·가격표를 바꾼 지\n1년이 넘었나요?",
    answers: ["네, 오래됐어요", "아니요, 최근에 바꿨어요"],
    need: 0,
    cardTitle: "새 메뉴판·가격표를 만들어 드려요",
    reason: "메뉴판을 바꾼 지 오래됐다고 하셨어요",
    exampleId: "finder-menu",
  },
  {
    question: "네이버 지도에 올라간\n가게 사진이 마음에 드나요?",
    answers: ["네, 괜찮아요", "아니요, 바꾸고 싶어요"],
    need: 1,
    cardTitle: "가게와 상품 사진을 새로 찍어 드려요",
    reason: "지도 속 가게 사진을 바꾸고 싶다고 하셨어요",
    exampleId: "finder-photo",
  },
  {
    question: "이번 달에 가게 SNS에\n글을 올렸나요?",
    answers: ["네, 올렸어요", "아니요, 못 올렸어요"],
    need: 1,
    cardTitle: "가게 SNS에 올릴 글을 만들어 드려요",
    reason: "이번 달 SNS에 글을 못 올렸다고 하셨어요",
    exampleId: "finder-sns",
  },
  {
    question: "손님 리뷰를 꼼꼼히\n읽어 볼 시간이 있나요?",
    answers: ["네, 챙겨 봐요", "아니요, 바빠서 못 봐요"],
    need: 1,
    cardTitle: "손님 리뷰에서 아쉬운 점을 찾아 드려요",
    reason: "리뷰를 읽을 시간이 없다고 하셨어요",
    exampleId: "example-review",
  },
  {
    question: "예약이나 단체 주문을\n전화로만 받아도 괜찮나요?",
    answers: ["네, 괜찮아요", "아니요, 온라인으로도 받고 싶어요"],
    need: 1,
    cardTitle: "전화 대신 받는 예약서를 만들어 드려요",
    reason: "온라인으로도 예약을 받고 싶다고 하셨어요",
    exampleId: "example-booking",
  },
  {
    question: "단골에게 주는 쿠폰이나\n도장카드가 있나요?",
    answers: ["네, 있어요", "아니요, 있으면 좋을 거 같아요"],
    need: 1,
    cardTitle: "단골 쿠폰·도장카드를 만들어 드려요",
    reason: "단골 쿠폰이 있으면 좋겠다고 하셨어요",
    exampleId: "example-coupon",
  },
  {
    question: "간판이나 현수막을\n바꾸고 싶지 않으신가요?",
    answers: ["네, 바꾸고 싶어요", "아니요, 지금이 좋아요"],
    need: 0,
    cardTitle: "새 간판·현수막 시안을 그려 드려요",
    reason: "간판이나 현수막을 바꾸고 싶다고 하셨어요",
    exampleId: "finder-sign",
  },
  {
    question: "네이버 지도 같은 곳에\n가게 소개 글이 채워져 있나요?",
    answers: ["네, 채워져 있어요", "아니요, 비어 있어요"],
    need: 1,
    cardTitle: "가게 소개 글을 써 드려요",
    reason: "가게 소개 글이 비어 있다고 하셨어요",
    exampleId: "finder-intro",
  },
  {
    question: "외국인 손님이\n자주 오나요?",
    answers: ["네, 자주 와요", "아니요, 거의 없어요"],
    need: 0,
    cardTitle: "메뉴·가게 안내를 영어로 옮겨 드려요",
    reason: "외국인 손님이 자주 온다고 하셨어요",
    exampleId: "finder-english",
  },
  {
    question: "새 메뉴·상품이나 할인을\n알릴 계획이 있나요?",
    answers: ["네, 있어요", "아니요, 아직 없어요"],
    need: 0,
    cardTitle: "새 메뉴·상품, 할인 전단지를 만들어 드려요",
    reason: "새 메뉴·상품이나 할인을 알릴 계획이 있다고 하셨어요",
    exampleId: "finder-flyer",
  },
  {
    question: "가게를 알릴 짧은 영상이\n필요하진 않나요?",
    answers: ["네, 필요해요", "아니요, 괜찮아요"],
    need: 0,
    cardTitle: "가게를 알리는 짧은 영상을 만들어 드려요",
    reason: "가게 영상이 필요하다고 하셨어요",
    exampleId: "finder-video",
  },
  {
    question: "손님을 끌기 위해 홍보나 이벤트\n기획이 필요하지 않으신가요?",
    answers: ["네, 필요해요", "아니요, 괜찮아요"],
    need: 0,
    cardTitle: "손님을 끄는 홍보·이벤트를 짜 드려요",
    reason: "홍보나 이벤트 기획이 필요하다고 하셨어요",
    exampleId: "finder-event",
  },
];

/** 추천 카드 하나 */
export interface TaskFinderPick {
  example: RequestExample;
  cardTitle: string;
  reason: string;
}

/** 추천 답이 하나도 없을 때의 추천 (⑫ → ⑪ → ⑩)과 그 이유 한 줄 */
const NO_MATCH: readonly { index: number; reason: string }[] = [
  { index: 11, reason: "손님을 더 모으고 싶을 때 좋아요" },
  { index: 10, reason: "SNS에 올리기 좋은 30초 영상이에요" },
  { index: 9, reason: "새 소식을 알릴 때 좋아요" },
];

/** 홈 예시 · 맡길 일 찾기 예시를 id 로 찾는다 */
export function findRequestExample(exampleId: string | undefined): RequestExample | undefined {
  return [...SAMPLE_REQUEST_EXAMPLES, ...TASK_FINDER_EXAMPLES].find((example) => example.id === exampleId);
}

const pickOf = (index: number, reason?: string): TaskFinderPick | undefined => {
  const question = TASK_FINDER_QUESTIONS[index];
  const example = findRequestExample(question?.exampleId);
  return question && example ? { example, cardTitle: question.cardTitle, reason: reason ?? question.reason } : undefined;
};

/**
 * 답(질문 순서대로 고른 답 0 · 1)으로 추천 3개를 고른다. 추천 답이 나온 질문 중 위 순서대로 3개,
 * 하나도 없으면 ⑫ → ⑪ → ⑩. matched 가 false 면 「해당 없음」 화면.
 */
export function taskFinderPicks(answers: readonly number[]): { matched: boolean; picks: TaskFinderPick[] } {
  const matched = TASK_FINDER_QUESTIONS.flatMap((question, i) => (answers[i] === question.need ? [i] : []));
  if (matched.length > 0) {
    return { matched: true, picks: matched.slice(0, 3).flatMap((i) => pickOf(i) ?? []) };
  }
  return { matched: false, picks: NO_MATCH.flatMap(({ index, reason }) => pickOf(index, reason) ?? []) };
}

/** 화면 기록에 들고 다니는 답. 질문 수보다 길거나 0 · 1 이 아니면 버린다 */
export function readTaskFinderAnswers(state: unknown): number[] | undefined {
  const answers = (state as { answers?: unknown } | null)?.answers;
  if (!Array.isArray(answers) || answers.length > TASK_FINDER_QUESTIONS.length) return undefined;
  return answers.every((a) => a === 0 || a === 1) ? (answers as number[]) : undefined;
}
