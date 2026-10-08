import { useParams } from "react-router-dom";
import { DownloadButton, FlowBar, LoadNotice, NoteBox, SubScreen, WorkKindIcon } from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  flowSteps,
  parsePositiveId,
  submissionFileName,
  useJobResult,
  useReceivedProposals,
  workHistoryText,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { fileSizeOfUrl } from "../lib/attachmentFormats";
import { formatMonthDay } from "../lib/date";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";
import "./OwnerDetailPage.css";
import "./OwnerWorkResultPage.css";

/**
 * 피그마 「지난 결과물 보기」. 원본 파일을 하나씩 받고, 작업 기록을 본다.
 * 서버 작업(GET /jobs/{id}/result, ADR 0036). 주소의 id 가 숫자가 아니면 찾을 수 없음.
 */
function OwnerWorkResultPage() {
  const { workId = "" } = useParams();
  const back = useBack(OWNER_PATHS.home);
  const jobId = parsePositiveId(workId);
  return jobId !== undefined ? <JobResultView jobId={jobId} /> : <OwnerMissing title="지난 결과물 보기" onBack={back} />;
}

/**
 * 서버 작업의 결과물. 파일마다 이름과 크기(files)를 보인다. 제안으로 시작했는지는
 * 받은 제안(GET /me/received-proposals)의 jobId 로 본다.
 */
function JobResultView({ jobId }: { jobId: number }) {
  const back = useBack(OWNER_PATHS.home);
  const { load, reload } = useJobResult(jobId);
  const { load: proposalsLoad } = useReceivedProposals();

  if (load.status === "notFound" || load.status === "closed") {
    return <OwnerMissing title="지난 결과물 보기" onBack={back} message="아직 끝나지 않은 작업이에요" />;
  }
  if (load.status !== "loaded") {
    return (
      <SubScreen title="지난 결과물 보기" onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status}
          loadingText="결과물을 불러오는 중이에요"
          errorText="결과물을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }

  const result = load.data;
  const proposal =
    proposalsLoad.status === "loaded" && proposalsLoad.proposals.some((p) => p.jobId === jobId);
  const who = studentTitle(result.studentName.trim() || "학생");
  const completedHow = result.normalCompleted ? "사장님이 직접 확인" : "7일 지나 자동 완료";

  return (
    <SubScreen title="지난 결과물 보기" onBack={back}>
      <div className="owner-detail owner-result">
        <div className="owner-detail__work">
          <div className="owner-detail__work-head">
            <WorkKindIcon kind={proposal ? "proposal" : "request"} size={22} />
            <h2 className="owner-detail__work-title">{result.title}</h2>
            <span className="owner-result__done">완료</span>
          </div>
          <p className="owner-detail__work-meta">
            {`${who}\n${formatMonthDay(result.completedAt)} 완료 (${completedHow})\n작업비 ${formatWon(result.workFee)} 정산 완료`}
          </p>
        </div>

        <FlowBar steps={flowSteps(proposal ? "제안" : "의뢰", 5)} />

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">원본 파일</h2>
          <ul className="owner-result__files">
            {result.fileUrls.map((url) => {
              const name = submissionFileName(url);
              const size = fileSizeOfUrl(result.files, url);
              return (
                <li key={url} className="owner-result__file">
                  <span className="owner-result__file-icon" aria-hidden="true">
                    📄
                  </span>
                  <span className="owner-result__file-info">
                    <span className="owner-result__file-name">{name}</span>
                    {size && <span className="owner-result__file-size">{size}</span>}
                  </span>
                  <DownloadButton href={url} aria-label={`${name} 받기`} />
                </li>
              );
            })}
          </ul>
        </section>

        {result.message.trim() && <NoteBox title={`${who}의 한마디`} body={result.message} />}

        {result.workHistory.length > 0 && (
          <div className="owner-result__history">
            <h2 className="owner-result__history-title">작업 기록</h2>
            <ul className="owner-result__history-list">
              {result.workHistory.map((item, i) => (
                <li key={i} className="owner-result__history-item">
                  <span className="owner-result__history-dot" aria-hidden="true" />
                  <span className="owner-result__history-date">{formatMonthDay(item.date)}</span>
                  <span>{workHistoryText(item.type, result.normalCompleted)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </SubScreen>
  );
}

export default OwnerWorkResultPage;
