import { BottomSheet, Button } from "../../../components";
import { REPORT_PROBLEM_TYPES, SUPPORT_EMAIL, reportMailto } from "../lib/support";
import "./ReportSheet.css";

interface ReportSheetProps {
  open: boolean;
  /** 메일 제목에 넣을 작업 이름 */
  workTitle: string;
  onClose: () => void;
}

/**
 * 피그마 「학생 문제 신고 - 메일 문의 안내 (팝업)」. 앱 안 신고 폼 없이
 * 운영 메일로 보내고, 운영자가 판단할 때까지 작업비는 가꿈이 보관한다.
 */
function ReportSheet({ open, workTitle, onClose }: ReportSheetProps) {
  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="학생에게 문제가 있나요?"
      description="메일로 알려 주시면 가꿈 운영자가 확인하고 결정해요."
      footer={
        <div className="report-sheet__actions">
          <Button variant="secondary" className="report-sheet__close" onClick={onClose}>
            닫기
          </Button>
          <Button
            className="report-sheet__send"
            onClick={() => {
              window.location.href = reportMailto(workTitle);
            }}
          >
            메일 보내기
          </Button>
        </div>
      }
    >
      <div className="report-sheet">
        <div className="report-sheet__to">
          <span>받는 곳</span>
          <strong>가꿈 운영팀 : {SUPPORT_EMAIL}</strong>
        </div>
        <hr className="report-sheet__divider" />
        <strong className="report-sheet__title">메일에 적어 주세요</strong>
        <ul className="report-sheet__list">
          <li>의뢰 이름</li>
          <li>
            문제 종류
            <span>{REPORT_PROBLEM_TYPES.join(" / ")}</span>
          </li>
          <li>자세한 설명</li>
          <li>증거 자료 (채팅 화면 · 사진)</li>
        </ul>
      </div>
    </BottomSheet>
  );
}

export default ReportSheet;
