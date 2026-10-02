import { createContext, useContext } from "react";
import type { StudentSignupDraft } from "../types";

export interface StudentSignupContextValue {
  draft: StudentSignupDraft;
  update: (patch: Partial<StudentSignupDraft>) => void;
}

export const StudentSignupContext = createContext<StudentSignupContextValue | null>(null);

/** 학생 가입 화면들이 같은 입력값을 읽고 고친다. StudentSignupProvider 안에서만 쓴다 */
export function useStudentSignup(): StudentSignupContextValue {
  const value = useContext(StudentSignupContext);
  if (!value) throw new Error("useStudentSignup must be used inside StudentSignupProvider");
  return value;
}
