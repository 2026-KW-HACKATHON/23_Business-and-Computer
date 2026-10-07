import { BottomSheet, Button, InfoRows, WorkPlan } from "../../../components";
import { formatMonthDay, formatMonthDayWeekday } from "../../../lib/date";
import { formatWon } from "../../../lib/money";
import type { StudentWork } from "../types";
import "./MyPlanSheet.css";

/** 시트에 보이는 작업. 채팅방은 보낸 날 · 작업 종류를 모른다 (지원서로 본다) */
type PlanSheetWork = Pick<StudentWork, "title" | "plan" | "budget" | "draftDue" | "finalDue" | "revisionLimit"> & {
  kind?: StudentWork["kind"];
  planSentOn?: string;
};

interface MyPlanSheetProps {
  /** 없으면 닫힌 상태 */
  work: PlanSheetWork | undefined;
  onClose: () => void;
}

/** 피그마 「작업계획서 보기 (채팅 팝업 · 학생)」. 내가 보낸 작업계획서와 작업 조건 */
function MyPlanSheet({ work, onClose }: MyPlanSheetProps) {
  const sentWith = work?.kind === "proposal" ? "제안할 때" : "지원할 때";
  return (
    <BottomSheet
      open={work !== undefined}
      onClose={onClose}
      title="내 작업계획서"
      description={
        work?.planSentOn
          ? `${work.title}, ${formatMonthDay(work.planSentOn)} ${sentWith} 보냄`
          : work?.title
      }
      footer={
        <Button variant="secondary" fullWidth onClick={onClose}>
          닫기
        </Button>
      }
    >
      {work && (
        <div className="my-plan-sheet">
          <WorkPlan plan={work.plan} />
          <div className="my-plan-sheet__terms">
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

export default MyPlanSheet;
