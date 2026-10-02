import { createContext, useContext } from "react";
import type { OwnerSignupDraft } from "../types";

export interface OwnerSignupContextValue {
  draft: OwnerSignupDraft;
  update: (patch: Partial<OwnerSignupDraft>) => void;
}

export const OwnerSignupContext = createContext<OwnerSignupContextValue | null>(null);

/** 사장님 가입 화면들이 같은 입력값을 읽고 고친다. OwnerSignupProvider 안에서만 쓴다 */
export function useOwnerSignup(): OwnerSignupContextValue {
  const value = useContext(OwnerSignupContext);
  if (!value) throw new Error("useOwnerSignup must be used inside OwnerSignupProvider");
  return value;
}
