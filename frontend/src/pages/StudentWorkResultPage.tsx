import { useNavigate, useParams } from "react-router-dom";
import {
  AttachmentTiles,
  DownloadButton,
  FlowBar,
  LoadNotice,
  NoteBox,
  SubScreen,
  TextButton,
} from "../components";
import {
  STUDENT_PATHS,
  StudentMissing,
  WorkSummary,
  flowSteps,
  useFinishedWork,
  useProposalJobIds,
  useStudentWork,
  workHistoryText,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { fileNameFromUrl } from "../lib/fileUrl";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/** 사진 파일이면 칸에 사진을 보여 준다 */
const IMAGE_NAME = /\.(jpe?g|png|webp|gif)$/i;

/**
 * 피그마 「내 결과물 보기」. 최종 결과물 · 원본 파일 · 사장님 후기 · 작업 기록.
 * 주소의 id 가 숫자면 서버 작업(GET /jobs/{id}/result, ADR 0042), 아니면 샘플 작업.
 */
function StudentWorkResultPage() {
  const { workId = "" } = useParams();
  const jobId = Number(workId);
  return Number.isSafeInteger(jobId) && jobId > 0 ? (
    <JobResultView jobId={jobId} />
  ) : (
    <SampleWorkResult workId={workId} />
  );
}

/**
 * 서버 작업의 결과물. 가게 이름은 GET /jobs/{id}, 사장님 후기는 GET /jobs/{id}/review 에서 보고,
 * 제안으로 시작했는지는 보낸 제안(GET /me/proposals)의 jobId 로 본다. 파일 크기는 서버가 주지 않아 이름만 보인다.
 */
function JobResultView({ jobId }: { jobId: number }) {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const { load, reload } = useFinishedWork(jobId);
  const proposalJobIds = useProposalJobIds();

  if (load.status === "notFound") {
    return <StudentMissing title="내 결과물" onBack={back} message="아직 끝나지 않은 작업이에요" />;
  }
  if (load.status !== "loaded") {
    return (
      <SubScreen title="내 결과물" onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="결과물을 불러오는 중이에요"
          errorText="결과물을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }

  const { result, storeName, review } = load.data;
  const kind = proposalJobIds.has(jobId) ? "proposal" : "request";
  const completedHow = result.normalCompleted ? "사장님이 직접 확인" : "7일 지나 자동 완료";
  const completed = `${formatMonthDay(result.completedAt)} 완료 (${completedHow})`;
  const names = result.fileUrls.map(fileNameFromUrl);
  const reviewText = review?.content?.trim();

  return (
    <SubScreen title="내 결과물" onBack={back}>
      <div className="student-detail">
        <WorkSummary
          kind={kind}
          title={result.title}
          right={<span className="student-work__chip">완료</span>}
          meta={`${[storeName, completed].filter(Boolean).join(" · ")}\n작업비 ${formatWon(result.workFee)} 정산 완료`}
        />

        <FlowBar tone="student" steps={flowSteps(kind === "proposal" ? "제안" : "의뢰", 5)} />

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">최종 수정안</h2>
          <AttachmentTiles
            names={names}
            srcs={result.fileUrls.map((url, i) => (IMAGE_NAME.test(names[i]) ? url : ""))}
            height={110}
          />
          <p className="student-work__hint">완료된 작업이라 「내 작업물 모아보기」에 자동으로 담겼어요</p>
        </section>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">원본 파일</h2>
          <ul className="student-work__files">
            {result.fileUrls.map((url, i) => (
              <li key={url} className="student-work__file">
                <span aria-hidden="true">📄</span>
                <span className="student-work__file-info">
                  <strong>{names[i]}</strong>
                </span>
                <DownloadButton href={url} aria-label={`${names[i]} 받기`} />
              </li>
            ))}
          </ul>
        </section>

        {review ? (
          <div className="student-work__review">
            <div className="student-work__review-head">
              <strong>{review.storeName?.trim() || storeName || "가게"} 사장님의 후기</strong>
              <span>★ {review.rating.toFixed(1)}</span>
            </div>
            {reviewText && <p>{reviewText}</p>}
            <TextButton onClick={() => navigate(STUDENT_PATHS.workReview(String(jobId)))}>
              받은 후기 보기
            </TextButton>
          </div>
        ) : (
          result.message.trim() && <NoteBox title="내가 남긴 한마디" body={result.message} />
        )}

        {result.workHistory.length > 0 && (
          <div className="student-work__history">
            <h2 className="student-detail__section-title">작업 기록</h2>
            <ul className="student-work__history-list">
              {result.workHistory.map((item, i) => (
                <li key={i} className="student-work__history-item">
                  <span className="student-work__history-dot" aria-hidden="true" />
                  <span className="student-work__history-date">{formatMonthDay(item.date)}</span>
                  <span>{workHistoryText(item.type, result.normalCompleted)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <p className="student-detail__footnote">
          사장님이 결과물을 받고 7일 동안 확인하지 않으면 자동으로 완료되고 작업비가 정산돼요.
        </p>
      </div>
    </SubScreen>
  );
}

/** 샘플 작업의 결과물 (알림 · 채팅의 예시) */
function SampleWorkResult({ workId }: { workId: string }) {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.home);
  const work = useStudentWork(workId);

  if (!work || work.status !== "completed") {
    return <StudentMissing title="내 결과물" onBack={back} message="아직 끝나지 않은 작업이에요" />;
  }

  const completedHow = work.completedBy === "auto" ? "7일 지나 자동 완료" : "사장님이 직접 확인";
  const completedOn = work.completedOn ? formatMonthDay(work.completedOn) : "";

  return (
    <SubScreen title="내 결과물" onBack={back}>
      <div className="student-detail">
        <WorkSummary
          kind={work.kind}
          title={work.title}
          right={<span className="student-work__chip">완료</span>}
          meta={`${work.store.name} · ${completedOn} 완료 (${completedHow})\n작업비 ${formatWon(work.budget)} 정산 완료`}
        />

        <FlowBar tone="student" steps={flowSteps(work.kind === "proposal" ? "제안" : "의뢰", 5)} />

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">최종 수정안</h2>
          {work.resultSummary && <p className="student-detail__text">{work.resultSummary}</p>}
          <AttachmentTiles names={work.files.map((f) => f.name)} height={110} />
          <p className="student-work__hint">완료된 작업이라 「내 작업물 모아보기」에 자동으로 담겼어요</p>
        </section>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">원본 파일</h2>
          <ul className="student-work__files">
            {work.files.map((file) => (
              <li key={file.name} className="student-work__file">
                <span aria-hidden="true">📄</span>
                <span className="student-work__file-info">
                  <strong>{file.name}</strong>
                  <small>{file.size}</small>
                </span>
                <DownloadButton aria-label={`${file.name} 받기`} />
              </li>
            ))}
          </ul>
        </section>

        {work.review ? (
          <div className="student-work__review">
            <div className="student-work__review-head">
              <strong>{work.store.name} 사장님의 후기</strong>
              <span>★ {work.review.rating.toFixed(1)}</span>
            </div>
            <p>{work.review.text}</p>
            <TextButton onClick={() => navigate(STUDENT_PATHS.workReview(work.id))}>받은 후기 보기</TextButton>
          </div>
        ) : (
          work.myMessage && <NoteBox title="내가 남긴 한마디" body={work.myMessage} />
        )}

        {work.history.length > 0 && (
          <div className="student-work__history">
            <h2 className="student-detail__section-title">작업 기록</h2>
            <ul className="student-work__history-list">
              {work.history.map((item, i) => (
                <li key={i} className="student-work__history-item">
                  <span className="student-work__history-dot" aria-hidden="true" />
                  <span className="student-work__history-date">{formatMonthDay(item.date)}</span>
                  <span>{item.text}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <p className="student-detail__footnote">
          사장님이 결과물을 받고 7일 동안 확인하지 않으면 자동으로 완료되고 작업비가 정산돼요.
        </p>
      </div>
    </SubScreen>
  );
}

export default StudentWorkResultPage;
