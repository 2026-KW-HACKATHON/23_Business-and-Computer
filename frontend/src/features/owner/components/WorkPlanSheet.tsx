import { BottomSheet, Button, InfoRows, RoleAvatar, WorkPlan } from "../../../components";
import { formatMonthDay, formatMonthDayWeekday } from "../../../lib/date";
import { formatWon } from "../../../lib/money";
import type { OwnerWork } from "../types";
import "./WorkPlanSheet.css";

/** 시트에 보이는 작업. 채팅방은 보낸 날을 모른다 (planSentOn 없음) */
type PlanSheetWork = Pick<OwnerWork, "title" | "plan" | "budget" | "draftDue" | "finalDue" | "revisionLimit"> & {
  student: { name: string };
  planSentOn?: string;
};

interface WorkPlanSheetProps {
  /** 없으면 닫힌 상태 */
  work: PlanSheetWork | undefined;
  onClose: () => void;
  /** 넣으면 「닫기」 옆에 「채팅하기」 (홈에서 열 때) */
  onChat?: () => void;
}

/** 「작업계획서 보기」 바텀시트. 학생이 지원할 때 보낸 계획서와 작업 조건 */
function WorkPlanSheet({ work, onClose, onChat }: WorkPlanSheetProps) {
  return (
    <BottomSheet
      open={work !== undefined}
      onClose={onClose}
      header={
        work && (
          <div className="work-plan-sheet__head">
            <RoleAvatar role="student" />
            <div className="work-plan-sheet__heading">
              <h2 className="work-plan-sheet__title">{work.student.name} 학생의 작업계획서</h2>
              <p className="work-plan-sheet__sub">
                {work.planSentOn
                  ? `${work.title}, ${formatMonthDay(work.planSentOn)} 지원할 때 보냄`
                  : work.title}
              </p>
            </div>
          </div>
        )
      }
      footer={
        <div className="work-plan-sheet__actions">
          <Button variant="secondary" onClick={onClose}>
            닫기
          </Button>
          {onChat && <Button onClick={onChat}>채팅하기</Button>}
        </div>
      }
    >
      {work && (
        <div className="work-plan-sheet__body">
          <WorkPlan plan={work.plan} />
          <div className="work-plan-sheet__terms">
            <InfoRows
              rows={[
                { label: "작업비", value: formatWon(work.budget) },
                { label: "초안 마감", value: formatMonthDayWeekday(work.draftDue) },
                { label: "최종 마감", value: formatMonthDayWeekday(work.finalDue) },
                { label: "수정", value: `${work.revisionLimit}회` },
              ]}
            />
          </div>
        </div>
      )}
    </BottomSheet>
  );
}

export default WorkPlanSheet;
