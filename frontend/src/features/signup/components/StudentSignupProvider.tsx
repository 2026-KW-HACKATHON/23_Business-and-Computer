import { useCallback, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { StudentSignupContext } from "../lib/studentSignupContext";
import { EMPTY_STUDENT_SIGNUP } from "../types";
import type { StudentSignupDraft } from "../types";

/**
 * 학생 가입 1/3 ~ 완료 화면이 입력값을 나눠 쓴다.
 * 메모리에만 두어서, 앱을 닫거나 새로고침하면 처음(역할 선택)부터 다시 한다.
 */
function StudentSignupProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<StudentSignupDraft>(EMPTY_STUDENT_SIGNUP);
  const update = useCallback((patch: Partial<StudentSignupDraft>) => {
    setDraft((prev) => ({ ...prev, ...patch }));
  }, []);
  const value = useMemo(() => ({ draft, update }), [draft, update]);

  return <StudentSignupContext.Provider value={value}>{children}</StudentSignupContext.Provider>;
}

export default StudentSignupProvider;
