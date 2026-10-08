import { useNavigate, useParams } from "react-router-dom";
import { Button, LoadNotice, SubScreen } from "../components";
import { OWNER_PATHS, OwnerMissing, parsePositiveId, useApplicantProfile } from "../features/owner";
import { useBack } from "../hooks/useBack";
import OwnerStudentProfileView from "./OwnerStudentProfileView";

/**
 * 피그마 「지원자 학생 프로필 보기」. GET /jobs/{id}/applications/{applicationId}/profile (ADR 0030).
 * 본문(전공역량 · 자격증 · 포트폴리오 · 후기)은 OwnerStudentProfileView, 아래에 「이 학생에게 맡기기」(결제 화면).
 */
function OwnerApplicantProfilePage() {
  const { requestId, applicationId } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.requestApplicants(requestId ?? ""));
  const { load, reload } = useApplicantProfile(
    parsePositiveId(requestId),
    parsePositiveId(applicationId),
  );

  if (load.status === "notFound") return <OwnerMissing title="지원자 프로필" onBack={back} />;
  if (load.status === "closed") {
    return (
      <OwnerMissing title="지원자 프로필" onBack={back} message="취소된 의뢰라 프로필을 볼 수 없어요" />
    );
  }

  const profile = load.status === "loaded" ? load.data : undefined;

  return (
    <SubScreen
      title="지원자 프로필"
      onBack={back}
      footer={
        profile && (
          <Button
            fullWidth
            onClick={() => navigate(OWNER_PATHS.assign(requestId ?? "", applicationId ?? ""))}
          >
            이 학생에게 맡기기
          </Button>
        )
      }
    >
      {profile ? (
        <OwnerStudentProfileView profile={profile} />
      ) : (
        <LoadNotice
          layout="page"
          status={load.status === "loading" ? "loading" : "error"}
          loadingText="프로필을 불러오는 중이에요"
          errorText="프로필을 불러오지 못했어요"
          onRetry={reload}
        />
      )}
    </SubScreen>
  );
}

export default OwnerApplicantProfilePage;
