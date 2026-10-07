import { BottomSheet, Button, InfoRows, RoleAvatar, WorkPlan } from "../../../components";
import { formatMonthDay, formatMonthDayWeekday } from "../../../lib/date";
import { studentTitle } from "../../../lib/korean";
import { formatWon } from "../../../lib/money";
import type { WorkPlanSheetContent } from "../types";
import "./WorkPlanSheet.css";

interface WorkPlanSheetProps {
  /** 없으면 닫힌 상태 */
  content: WorkPlanSheetContent | undefined;
  onClose: () => void;
}

/** 채팅방 「작업계획서 보기」 바텀시트. 학생이 지원할 때 보낸 계획서와 작업 조건 (모르는 조건은 뺀다) */
function WorkPlanSheet({ content, onClose }: WorkPlanSheetProps) {
  const rows = content
    ? [
        ...(content.budget !== undefined ? [{ label: "작업비", value: formatWon(content.budget) }] : []),
        { label: "초안 마감", value: formatMonthDayWeekday(content.draftDue) },
        { label: "최종 마감", value: formatMonthDayWeekday(content.finalDue) },
        ...(content.revisionLimit !== undefined ? [{ label: "수정", value: `${content.revisionLimit}회` }] : []),
      ]
    : [];
  return (
    <BottomSheet
      open={content !== undefined}
      onClose={onClose}
      header={
        content && (
          <div className="work-plan-sheet__head">
            <RoleAvatar role="student" />
            <div className="work-plan-sheet__heading">
              <h2 className="work-plan-sheet__title">
                {content.studentName ? studentTitle(content.studentName) : "학생"}의 작업계획서
              </h2>
              <p className="work-plan-sheet__sub">
                {content.title}, {content.sentOn ? `${formatMonthDay(content.sentOn)} ` : ""}지원할 때 보냄
              </p>
            </div>
          </div>
        )
      }
      footer={
        <Button variant="secondary" fullWidth onClick={onClose}>
          닫기
        </Button>
      }
    >
      {content && (
        <div className="work-plan-sheet__body">
          <WorkPlan plan={content.plan} />
          <div className="work-plan-sheet__terms">
            <InfoRows rows={rows} />
          </div>
        </div>
      )}
    </BottomSheet>
  );
}

export default WorkPlanSheet;
