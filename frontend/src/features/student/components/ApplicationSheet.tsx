import { BottomSheet, Button, InfoRows, WorkPlan } from "../../../components";
import { formatMonthDay, formatMonthDayWeekday } from "../../../lib/date";
import { formatWon } from "../../../lib/money";
import type { StudentApplication, StudentRequest } from "../types";
import "./ApplicationSheet.css";

interface ApplicationSheetProps {
  /** 없으면 닫힌다 */
  application?: StudentApplication;
  request?: StudentRequest;
  onClose: () => void;
}

/**
 * 피그마 「내 지원서 보기 (팝업)」 · 「지원 결과 보기 (팝업)」. 내 활동 · 지원한 의뢰에서
 * 지원할 때 보낸 작업계획서를 다시 보고, 뽑히지 않았으면 제목 아래에 결과를 알린다.
 */
function ApplicationSheet({ application, request, onClose }: ApplicationSheetProps) {
  const notSelected = application?.status === "notSelected";
  const plan = application?.plan;

  return (
    <BottomSheet
      open={application !== undefined && request !== undefined}
      onClose={onClose}
      title={notSelected ? "지원 결과" : "내 지원서"}
      description={
        application && request
          ? notSelected
            ? `${request.title}, 이번에는 선택되지 않았어요`
            : `${request.title}, ${formatMonthDay(application.appliedOn)} 지원할 때 보냄`
          : undefined
      }
      footer={
        <Button variant="secondary" fullWidth onClick={onClose}>
          닫기
        </Button>
      }
    >
      {plan && request && (
        <div className="application-sheet">
          <WorkPlan plan={plan} />
          <div className="application-sheet__terms">
            <InfoRows
              rows={[
                { label: "작업비", value: formatWon(request.budget) },
                { label: "초안 마감", value: formatMonthDayWeekday(request.draftDue) },
                { label: "최종 마감", value: formatMonthDayWeekday(request.finalDue) },
                { label: "수정", value: `${request.revisionLimit}회` },
              ]}
            />
          </div>
        </div>
      )}
    </BottomSheet>
  );
}

export default ApplicationSheet;
