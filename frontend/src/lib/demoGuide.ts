/**
 * 둘러보기 첫 안내 (ADR 0053). 역할 선택에서 둘러보기를 시작하면 pending, 닫으면 seen. 이 브라우저 탭에만
 * 두어 탭을 닫고 다시 시작하면 또 보이고, 같은 탭에서는 한 번만 보인다
 */
const KEY = "demoGuide";

/** 역할 선택에서 둘러보기를 시작할 때. 이 탭에서 아직 안 봤으면 다음 홈에서 띄운다 */
export function markDemoGuide(): void {
  try {
    if (sessionStorage.getItem(KEY) !== "seen") sessionStorage.setItem(KEY, "pending");
  } catch {
    // 저장소를 못 쓰면 안내 없이 간다
  }
}

export function pendingDemoGuide(): boolean {
  try {
    return sessionStorage.getItem(KEY) === "pending";
  } catch {
    return false;
  }
}

/** 알겠어요 · 닫기 · 바깥 누르기로 닫으면 이 탭에서는 다시 띄우지 않는다 */
export function finishDemoGuide(): void {
  try {
    sessionStorage.setItem(KEY, "seen");
  } catch {
    // 저장소를 못 쓰면 남길 것도 없다
  }
}
