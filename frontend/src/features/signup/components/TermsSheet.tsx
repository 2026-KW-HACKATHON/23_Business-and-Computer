import { BottomSheet, Button } from "../../../components";
import type { Role } from "../../../types/role";
import { TERMS_SECTIONS } from "../lib/terms";
import "./TermsSheet.css";

interface TermsSheetProps {
  open: boolean;
  onClose: () => void;
  /** 확인 버튼 색 */
  tone: Role;
}

/** 이용약관 안내 바텀시트. 가입 1단계의 「보기」에서 연다 */
function TermsSheet({ open, onClose, tone }: TermsSheetProps) {
  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="가꿈 이용약관"
      description="가입 전에 꼭 확인해 주세요"
      footer={
        <Button fullWidth tone={tone} onClick={onClose}>
          확인
        </Button>
      }
    >
      <div className="terms-sheet">
        {TERMS_SECTIONS.map(({ title, body }) => (
          <section key={title} className="terms-sheet__section">
            <h3 className="terms-sheet__title">{title}</h3>
            <p className="terms-sheet__body">{body}</p>
          </section>
        ))}
      </div>
    </BottomSheet>
  );
}

export default TermsSheet;
