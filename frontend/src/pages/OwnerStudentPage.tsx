import { useParams } from "react-router-dom";
import { LoadNotice, SubScreen } from "../components";
import { OWNER_PATHS, OwnerMissing, parsePositiveId, useOwnerStudentProfile } from "../features/owner";
import { useBack } from "../hooks/useBack";
import OwnerStudentProfileView from "./OwnerStudentProfileView";

/**
 * 학생 프로필 (/owner/students/:id). GET /students/{id}/profile (ADR 0038).
 * 진행 중 작업 · 받은 제안 · 제안서의 「프로필 보기」가 연다. 본문은 지원자 프로필과 같은
 * OwnerStudentProfileView 이고, 아래 버튼은 없다. 숫자가 아닌 id 는 「찾는 학생이 없어요」.
 */
function OwnerStudentPage() {
  const { studentId } = useParams();
  const back = useBack(OWNER_PATHS.home);
  const { load, reload } = useOwnerStudentProfile(parsePositiveId(studentId));

  if (load.status === "notFound") {
    return <OwnerMissing title="학생 프로필" onBack={back} message="찾는 학생이 없어요" />;
  }
  return (
    <SubScreen title="학생 프로필" onBack={back}>
      {load.status === "loaded" ? (
        <OwnerStudentProfileView profile={load.data} />
      ) : (
        <LoadNotice
          status={load.status === "loading" ? "loading" : "error"}
          loadingText="프로필을 불러오는 중이에요"
          errorText="프로필을 불러오지 못했어요"
          onRetry={reload}
        />
      )}
    </SubScreen>
  );
}

export default OwnerStudentPage;
