import { BottomSheet, Button, InfoRows, WorkPlan } from "../../../components";
import { useJobDetail } from "../../explore";
import { formatMonthDayWeekday } from "../../../lib/date";
import { formatWon } from "../../../lib/money";
import { appliedOnText, appliedPlan } from "../lib/appliedJobs";
import type { AppliedJob } from "../lib/appliedJobs";
import "./ApplicationSheet.css";

interface ApplicationSheetProps {
  /** 없으면 닫힌다 */
  job?: AppliedJob;
  onClose: () => void;
}

/**
 * 피그마 「내 지원서 보기 (팝업)」 · 「지원 결과 보기 (팝업)」. 내 활동 · 지원한 의뢰에서
 * 지원한 의뢰의 조건을 다시 보고, 뽑히지 않았으면 제목 아래에 결과를 알린다.
 * 수정 횟수는 열 때 GET /jobs/{id} 로 읽고(그동안은 그 줄을 숨김), 내가 보낸 지원서(한 줄 요약 ·
 * 작업계획서 · 결과물)는 지원 목록이 줄 때만 보인다.
 */
function ApplicationSheet({ job, onClose }: ApplicationSheetProps) {
  const { load } = useJobDetail(job ? String(job.jobId) : undefined);
  const notSelected = job?.applicationStatus === "REJECTED";
  const plan = job && appliedPlan(job);
  const sentOn = job && appliedOnText(job);
  const revisionCount = load.status === "loaded" ? load.job.revisionCount : undefined;

  return (
    <BottomSheet
      open={job !== undefined}
      onClose={onClose}
      title={notSelected ? "지원 결과" : "내 지원서"}
      description={
        job
          ? notSelected
            ? `${job.title}, 이번에는 선택되지 않았어요`
            : sentOn
              ? `${job.title}, ${sentOn}`
              : job.title
          : undefined
      }
      footer={
        <Button variant="secondary" fullWidth onClick={onClose}>
          닫기
        </Button>
      }
    >
      {job && (
        <div className="application-sheet">
          {plan && <WorkPlan plan={plan} />}
          <div className="application-sheet__terms">
            <InfoRows
              rows={[
                { label: "작업비", value: formatWon(job.budget) },
                { label: "초안 마감", value: formatMonthDayWeekday(job.draftDeadline) },
                { label: "최종 마감", value: formatMonthDayWeekday(job.finalDeadline) },
                ...(revisionCount === undefined ? [] : [{ label: "수정", value: `${revisionCount}회` }]),
              ]}
            />
          </div>
        </div>
      )}
    </BottomSheet>
  );
}

export default ApplicationSheet;
