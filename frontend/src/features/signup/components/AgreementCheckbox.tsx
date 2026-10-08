import { useState } from "react";
import { BottomSheet, Button, Checkbox } from "../../../components";
import type { Role } from "../../../types/role";
import { AGREEMENT_SECTIONS } from "../lib/terms";
import "./TermsSheet.css";

interface AgreementCheckboxProps {
  /** 시트 설명과 「동의해요」 색 */
  tone: Role;
  checked: boolean;
  onChange: (checked: boolean) => void;
}

/**
 * 「책임 약관과 취소·환불 기준에 동의해요 (필수)」 체크 (ADR 0054). 비어 있을 때 누르면 그 약관 바텀시트가 열리고,
 * 시트의 「동의해요」를 눌러야 체크된다. 체크된 것을 누르면 바로 풀린다. 결제 · 제안 수락(사장님)과
 * 작업 시작(학생)이 쓴다
 */
function AgreementCheckbox({ tone, checked, onChange }: AgreementCheckboxProps) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Checkbox
        checked={checked}
        onChange={(next) => (next ? setOpen(true) : onChange(false))}
        label="책임 약관과 취소·환불 기준에 동의해요 (필수)"
      />
      <BottomSheet
        open={open}
        onClose={() => setOpen(false)}
        title="책임 약관과 취소·환불 기준"
        description={tone === "owner" ? "결제하기 전에 꼭 확인해 주세요" : "작업을 시작하기 전에 꼭 확인해 주세요"}
        footer={
          <Button
            fullWidth
            tone={tone}
            onClick={() => {
              onChange(true);
              setOpen(false);
            }}
          >
            동의해요
          </Button>
        }
      >
        <div className="terms-sheet">
          {AGREEMENT_SECTIONS.map(({ title, body }) => (
            <section key={title} className="terms-sheet__section">
              <h3 className="terms-sheet__title">{title}</h3>
              <p className="terms-sheet__body">{body}</p>
            </section>
          ))}
        </div>
      </BottomSheet>
    </>
  );
}

export default AgreementCheckbox;
