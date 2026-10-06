import type { OwnerStore } from "../types";

/*
 * 가게 정보 임시 예시 데이터 (피그마 「가게 정보 수정」 내용).
 * 내 정보 머리(가게 이름 · 대표자 · 주소)도 이 값을 쓴다.
 */

export const SAMPLE_STORE: OwnerStore = {
  storeName: "치킨플러스",
  category: "음식점",
  address: "서울 노원구 석계로 13길 35",
  addressDetail: "세영청마루아파트 상가동 1층 101호",
  intro: "광운대 앞 20년 된 치킨집이에요. 학생 손님이 많아요.",
  representative: "이새빛",
  businessNumber: "123-45-67890",
};
