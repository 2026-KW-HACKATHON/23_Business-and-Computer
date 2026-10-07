/** 서버에 올라간 파일 주소의 끝 경로가 올린 파일 이름이다 (초안 · 수정안 · 최종 결과물) */
export function fileNameFromUrl(url: string): string {
  const last = url.split("?")[0].split("/").pop() ?? "";
  try {
    return decodeURIComponent(last) || "파일";
  } catch {
    return last || "파일";
  }
}
