import { useCallback, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { OwnerSignupContext } from "../lib/ownerSignupContext";
import { EMPTY_OWNER_SIGNUP } from "../types";
import type { OwnerSignupDraft } from "../types";

/**
 * 사장님 가입 1/3 ~ 완료 화면이 입력값을 나눠 쓴다.
 * 메모리에만 두어서, 앱을 닫거나 새로고침하면 처음(역할 선택)부터 다시 한다.
 */
function OwnerSignupProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<OwnerSignupDraft>(EMPTY_OWNER_SIGNUP);
  const update = useCallback((patch: Partial<OwnerSignupDraft>) => {
    setDraft((prev) => ({ ...prev, ...patch }));
  }, []);
  const value = useMemo(() => ({ draft, update }), [draft, update]);

  return <OwnerSignupContext.Provider value={value}>{children}</OwnerSignupContext.Provider>;
}

export default OwnerSignupProvider;
