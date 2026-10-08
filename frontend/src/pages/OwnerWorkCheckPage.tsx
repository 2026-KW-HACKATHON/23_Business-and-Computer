import { useParams } from "react-router-dom";
import { OWNER_PATHS, OwnerMissing, parsePositiveId } from "../features/owner";
import { useBack } from "../hooks/useBack";
import OwnerJobCheckPage from "./OwnerJobCheckPage";

/**
 * 피그마 「작업 확인 · 초안」 · 「작업 확인 · 수정안」.
 * 서버 작업(OwnerJobCheckPage, ADR 0035). 주소의 id 가 숫자가 아니면 찾을 수 없음.
 */
function OwnerWorkCheckPage() {
  const { workId = "" } = useParams();
  const back = useBack(OWNER_PATHS.home);
  const jobId = parsePositiveId(workId);
  return jobId !== undefined ? <OwnerJobCheckPage jobId={jobId} /> : <OwnerMissing title="작업 확인" onBack={back} />;
}

export default OwnerWorkCheckPage;
