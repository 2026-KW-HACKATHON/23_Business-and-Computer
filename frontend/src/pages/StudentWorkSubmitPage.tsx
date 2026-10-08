import { useParams } from "react-router-dom";
import { STUDENT_PATHS, StudentMissing } from "../features/student";
import { useBack } from "../hooks/useBack";
import StudentJobSubmitPage from "./StudentJobSubmitPage";

/**
 * 피그마 「작업 진행 · 제출」. 초안 파일과 사장님께 한마디를 올린다.
 * 보내면 「제출 완료 팝업창」 → 내 활동 (진행 중).
 */
function StudentWorkSubmitPage() {
  const { workId = "" } = useParams();
  const back = useBack(STUDENT_PATHS.home);
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <StudentJobSubmitPage jobId={jobId} kind="draft" />
  ) : (
    <StudentMissing title="초안 제출" onBack={back} />
  );
}

export default StudentWorkSubmitPage;
