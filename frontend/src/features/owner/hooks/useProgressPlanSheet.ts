import { useCallback, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { loadProgressPlan, progressWorkPlanContent } from "../lib/progressJobs";
import type { OwnerProgressJob } from "../lib/progressJobs";
import type { WorkPlanSheetContent } from "../types";

/**
 * 진행 중 작업의 작업계획서 바텀시트 (의뢰에 지원해 맡은 작업). 지원서는 진행 중 목록에 없어서
 * 누를 때 채팅방(GET /me/chat-rooms)에서 찾는다. 불러오는 동안 다시 눌러도 한 번만 부르고,
 * 못 찾거나 실패하면 알림으로 알린다. 401 은 /login 으로 보낸다.
 */
export function useProgressPlanSheet(): {
  content: WorkPlanSheetContent | undefined;
  open: (job: OwnerProgressJob) => void;
  close: () => void;
} {
  const navigate = useNavigate();
  const [content, setContent] = useState<WorkPlanSheetContent>();
  const loading = useRef(false);

  const open = useCallback(
    (job: OwnerProgressJob) => {
      if (loading.current) return;
      loading.current = true;
      void loadProgressPlan(job.jobId).then((result) => {
        loading.current = false;
        if (result.status === "loaded") setContent(progressWorkPlanContent(job, result.plan));
        else if (result.status === "unauthorized") navigate("/login", { replace: true });
        else if (result.status === "missing") window.alert("지원서를 찾지 못했어요");
        else window.alert("지원서를 불러오지 못했어요. 잠시 후 다시 시도해 주세요");
      });
    },
    [navigate],
  );
  const close = useCallback(() => setContent(undefined), []);

  return { content, open, close };
}
