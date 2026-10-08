import { useParams } from "react-router-dom";
import { STUDENT_PATHS, StudentMissing } from "../features/student";
import { useBack } from "../hooks/useBack";
import { StudentJobRevisionPage } from "./StudentJobStagePages";

/**
 * 피그마 「수정 요청 확인」. 사장님이 보낸 수정 요청과 내가 보낸 초안을 보고
 * 「수정안 작성하기」로 넘어간다. 질문은 「문의하기」(채팅방).
 */
function StudentRevisionPage() {
  const { workId = "" } = useParams();
  const back = useBack(STUDENT_PATHS.home);
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <StudentJobRevisionPage jobId={jobId} />
  ) : (
    <StudentMissing title="수정 요청 확인" onBack={back} />
  );
}

export default StudentRevisionPage;
