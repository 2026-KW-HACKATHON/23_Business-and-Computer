import type { Store } from "../types";

/*
 * 월계1동 가게 임시 예시 데이터 (피그마 「학생 탐색 · 가게」 · 「제안 보내기 1/4」).
 * 등록순. 치킨플러스는 사장님 예시(features/owner)의 우리 가게와 같다.
 */

export const SAMPLE_STORES: Store[] = [
  { id: "store-chicken", name: "치킨플러스", category: "음식점", address: "서울 노원구 석계로 13길 35" },
  { id: "store-kwcafe", name: "광운카페", category: "카페", address: "서울 노원구 광운로 20" },
  { id: "store-bunsik", name: "월계분식", category: "음식점", address: "서울 노원구 석계로 7" },
  { id: "store-dino", name: "공룡카페", category: "카페", address: "서울 노원구 광운로 15길 8" },
  { id: "store-happyhair", name: "해피헤어", category: "미용", address: "서울 노원구 석계로 11" },
  { id: "store-laundry", name: "행복세탁", category: "생활 서비스", address: "서울 노원구 광운로 3길 21" },
  { id: "store-banjeom", name: "월계반점", category: "음식점", address: "서울 노원구 석계로 21" },
  { id: "store-bakery", name: "동네빵집", category: "음식점", address: "서울 노원구 월계로 45" },
  { id: "store-alley", name: "골목카페", category: "카페", address: "서울 노원구 월계로 12" },
  { id: "store-kwhair", name: "광운헤어", category: "미용", address: "서울 노원구 광운로 9" },
];
