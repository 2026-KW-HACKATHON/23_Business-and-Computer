import { useParams } from "react-router-dom";
import { STUDENT_PATHS, StudentMissing } from "../features/student";
import { useBack } from "../hooks/useBack";
import { StudentJobSubmittedPage } from "./StudentJobStagePages";

/**
 * 피그마 「제출한 초안 보기」. 사장님이 확인하는 동안 낸 파일과 남긴 말을 다시 본다.
 * 사장님에게 문제가 있으면 오른쪽 위 「신고」(메일 문의 안내).
 */
function StudentSubmittedPage() {
  const { workId = "" } = useParams();
  const back = useBack(STUDENT_PATHS.home);
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <StudentJobSubmittedPage jobId={jobId} />
  ) : (
    <StudentMissing title="제출한 결과물" onBack={back} />
  );
}

export default StudentSubmittedPage;
