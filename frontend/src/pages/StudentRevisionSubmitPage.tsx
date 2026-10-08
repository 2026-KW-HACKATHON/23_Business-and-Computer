import { useParams } from "react-router-dom";
import { STUDENT_PATHS, StudentMissing } from "../features/student";
import { useBack } from "../hooks/useBack";
import StudentJobSubmitPage from "./StudentJobSubmitPage";

/**
 * 피그마 「수정안 작성하기 수정안 제출」. 사장님 수정 요청을 보며 최종본 파일과 메시지를 올린다.
 * 보내면 「수정안 제출 완료 팝업창」 → 내 활동 (진행 중).
 */
function StudentRevisionSubmitPage() {
  const { workId = "" } = useParams();
  const back = useBack(STUDENT_PATHS.home);
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <StudentJobSubmitPage jobId={jobId} kind="revision" />
  ) : (
    <StudentMissing title="수정안 제출" onBack={back} />
  );
}

export default StudentRevisionSubmitPage;
