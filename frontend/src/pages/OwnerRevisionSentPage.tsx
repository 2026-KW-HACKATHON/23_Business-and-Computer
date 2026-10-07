import { useParams } from "react-router-dom";
import { Button, LoadNotice, ReferencePhotos, SubScreen, WorkKindIcon } from "../components";
import { useJobDetail } from "../features/explore";
import {
  OWNER_PATHS,
  OwnerMissing,
  parsePositiveId,
  useLatestJobSubmission,
  useOwnerProgressJobs,
  useProposalJobIds,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, koreaDateOfUtc } from "../lib/date";
import { studentTitle } from "../lib/korean";
import "./OwnerRevisionPage.css";

/**
 * 피그마 「보낸 수정 요청 보기 (사장님)」 (ADR 0045). 작업 이력의 「수정 요청」 줄. 수정 요청 화면과 같은
 * 틀로, 내가 보낸 요청 내용과 참고 사진을 읽기만 한다. 마지막 결과물의 수정 요청을
 * GET /jobs/{id}/submissions/latest 로 읽는데, 서버가 지금은 맡은 학생에게만 열어 두어 그 전까지는 안내만 보인다
 */
function OwnerRevisionSentPage() {
  const { workId } = useParams();
  const jobId = parsePositiveId(workId);
  const back = useBack(OWNER_PATHS.chats);
  const { load, reload } = useLatestJobSubmission(jobId);
  const { load: jobLoad } = useJobDetail(workId);
  const { load: progressLoad } = useOwnerProgressJobs();
  const proposalJobIds = useProposalJobIds();

  if (jobId === undefined) return <OwnerMissing title="보낸 수정 요청" onBack={back} />;
  if (load.status === "notFound" || load.status === "closed") {
    return <OwnerMissing title="보낸 수정 요청" onBack={back} message="보낸 수정 요청은 곧 여기서 볼 수 있어요" />;
  }

  const request = load.status === "loaded" ? load.data.revisionRequest : undefined;
  const job = jobLoad.status === "loaded" ? jobLoad.job : undefined;
  const matched = progressLoad.status === "loaded" ? progressLoad.jobs.find((j) => j.jobId === jobId) : undefined;
  const revising = matched?.stage === "revising";
  // 「박지은 학생, 9월 23일 수정 요청, 수정 1/1」
  const meta = [
    matched?.student.name ? studentTitle(matched.student.name) : undefined,
    request ? `${formatMonthDay(koreaDateOfUtc(request.requestedAt))} 수정 요청` : undefined,
    load.status === "loaded" && job ? `수정 ${load.data.revisionNumber + 1}/${job.revisionCount}` : undefined,
  ]
    .filter(Boolean)
    .join(", ");
  const photos = request?.referenceImageUrls ?? [];

  return (
    <SubScreen
      title="보낸 수정 요청"
      onBack={back}
      footer={
        <Button fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      {load.status !== "loaded" && (
        <LoadNotice
          status={load.status}
          loadingText="수정 요청을 불러오는 중이에요"
          errorText="수정 요청을 불러오지 못했어요"
          onRetry={reload}
        />
      )}
      {load.status === "loaded" && !request && (
        <p className="owner-revision__note">이 결과물에는 보낸 수정 요청이 없어요</p>
      )}
      {request && (
        <div className="owner-revision">
          <section className="owner-revision__work">
            <div className="owner-revision__work-head">
              <WorkKindIcon kind={proposalJobIds.has(jobId) ? "proposal" : "request"} size={22} />
              <h2 className="owner-revision__work-title">{job?.title ?? ""}</h2>
            </div>
            {meta && <p className="owner-revision__meta">{meta}</p>}
          </section>

          <div className="owner-revision__intro">
            <h2 className="owner-revision__title">이렇게 고쳐 달라고 했어요</h2>
            <p className="owner-revision__description">추가 자료나 질문은 채팅으로 보내 주세요.</p>
          </div>

          <section className="request-field">
            <h3 className="request-field__label">요청 내용</h3>
            <div className="request-field__textarea-box">
              <p className="owner-revision__sent-text">{request.message?.trim() || "적은 내용이 없어요"}</p>
            </div>
          </section>

          {photos.length > 0 && (
            <section className="request-field">
              <h3 className="request-field__label">참고 사진</h3>
              <ReferencePhotos urls={photos} />
            </section>
          )}

          {revising && job && (
            <p className="owner-revision__note">학생은 최종 마감({formatMonthDay(job.finalDeadline)})까지 수정안을 보내요</p>
          )}
        </div>
      )}
    </SubScreen>
  );
}

export default OwnerRevisionSentPage;
