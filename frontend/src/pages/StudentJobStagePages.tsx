import { useState } from "react";
import type { ReactNode } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import {
  Button,
  DownloadButton,
  FlowBar,
  LoadNotice,
  NoteBox,
  ReferencePhotos,
  ReportSheet,
  SubScreen,
  TurnNotice,
} from "../components";
import {
  STUDENT_PATHS,
  StudentMissing,
  WorkSummary,
  isLastRevision,
  progressFlowSteps,
  progressMeta,
  progressStagePath,
  submissionDay,
  useLatestSubmission,
  useProgressJobs,
} from "../features/student";
import type { LatestSubmissionLoad, ProgressJob, ProgressStage } from "../features/student";
import { useBack } from "../hooks/useBack";
import { addDays, formatMonthDay } from "../lib/date";
import { fileNameFromUrl } from "../lib/fileUrl";
import "./StudentDetailPage.css";
import "./StudentWorkPage.css";

/**
 * 나와 매칭된 진행 중 작업 하나를 불러와 그 단계일 때만 그린다 (GET /me/jobs?status=MATCHED).
 * 불러오는 중 · 실패면 안내, 목록에 없으면 「진행 중인 작업이 아니에요」, 다른 단계면 그 단계 화면으로.
 */
function StageGate({
  jobId,
  stage,
  title,
  children,
}: {
  jobId: number;
  stage: ProgressStage;
  title: string;
  children: (job: ProgressJob, back: () => void) => ReactNode;
}) {
  const back = useBack(STUDENT_PATHS.home);
  const { load, reload } = useProgressJobs();

  if (load.status !== "loaded") {
    return (
      <SubScreen title={title} onBack={back}>
        <LoadNotice
          status={load.status}
          loadingText="작업을 불러오는 중이에요"
          errorText="작업을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }
  const job = load.jobs.find((j) => j.jobId === jobId);
  if (!job) return <StudentMissing title={title} onBack={back} message="진행 중인 작업이 아니에요" />;
  if (job.stage !== stage) return <Navigate to={progressStagePath(job)} replace />;
  return children(job, back);
}

/** 내가 낸 결과물을 불러오는 중 · 실패 안내 */
function SubmissionLoadNotice({ load, onRetry }: { load: LatestSubmissionLoad; onRetry: () => void }) {
  return (
    <LoadNotice
      status={load.status === "loading" ? "loading" : "error"}
      loadingText="낸 결과물을 불러오는 중이에요"
      errorText={load.status === "notFound" ? "낸 결과물을 찾지 못했어요" : "낸 결과물을 불러오지 못했어요"}
      onRetry={onRetry}
    />
  );
}

/** 내가 낸 파일. 이름은 주소 끝 경로이고, 「받기」는 새 창에서 파일을 연다 */
function SentFiles({ urls, note }: { urls: string[]; note?: string }) {
  return (
    <ul className="student-work__files">
      {urls.map((url) => {
        const name = fileNameFromUrl(url);
        return (
          <li key={url} className="student-work__file">
            <span aria-hidden="true">📄</span>
            <span className="student-work__file-info">
              <strong>{name}</strong>
              {note && <small>{note}</small>}
            </span>
            <DownloadButton href={url} aria-label={`${name} 받기`} />
          </li>
        );
      })}
    </ul>
  );
}

/**
 * 피그마 「수정 요청 확인」 (서버 작업, ADR 0032 · 0038). 사장님이 적은 고칠 곳 · 참고 사진과
 * 내가 보낸 초안 · 수정안 파일 (GET /jobs/{id}/submissions/latest).
 * 「수정안 작성하기」 → 수정안 제출, 「문의하기」 → 채팅.
 */
export function StudentJobRevisionPage({ jobId }: { jobId: number }) {
  return (
    <StageGate jobId={jobId} stage="revising" title="수정 요청 확인">
      {(job, back) => <RevisionScreen job={job} back={back} />}
    </StageGate>
  );
}

function RevisionScreen({ job, back }: { job: ProgressJob; back: () => void }) {
  const navigate = useNavigate();
  const { load, reload } = useLatestSubmission(job.jobId);
  const latest = load.status === "loaded" ? load.submission : undefined;
  const request = latest?.revisionRequest;
  const photos = request?.referenceImageUrls ?? [];
  const due = formatMonthDay(job.finalDeadline);
  const owner = job.storeName ? `${job.storeName} 사장님` : "사장님";
  const sent = (latest ? latest.submissionType === "REVISION" : job.revisionSubmitted) ? "수정안" : "초안";

  return (
    <SubScreen
      title="수정 요청 확인"
      onBack={back}
      footer={
        <div className="student-detail__actions">
          <Button variant="secondary" onClick={() => navigate(STUDENT_PATHS.chats)}>
            문의하기
          </Button>
          <Button tone="student" onClick={() => navigate(STUDENT_PATHS.workRevisionSubmit(String(job.jobId)))}>
            수정안 작성하기
          </Button>
        </div>
      }
    >
      <div className="student-detail">
        <WorkSummary kind={job.kind} title={job.title} meta={job.storeName ? owner : ""} />

        <FlowBar tone="student" steps={progressFlowSteps(job, `${due}까지`)} />

        <TurnNotice
          tone="student"
          title={`${due}까지 수정안을 올려 주세요`}
          description={latest && isLastRevision(latest, job.revisionLimit) ? "이번이 마지막 수정이에요" : undefined}
        />

        {latest ? (
          <>
            <section className="student-detail__section">
              <h2 className="student-detail__section-title">{owner}의 수정 요청</h2>
              <div className="student-work__quote">
                {request && <small>{formatMonthDay(submissionDay(request.requestedAt))}</small>}
                <p>{request?.message?.trim() || "사장님이 적은 내용이 없어요"}</p>
                {photos.length > 0 && (
                  <>
                    <span className="student-work__label">참고 사진</span>
                    <ReferencePhotos urls={photos} />
                  </>
                )}
              </div>
            </section>

            <section className="student-detail__section">
              <h2 className="student-detail__section-title">내가 보낸 {sent}</h2>
              <SentFiles
                urls={latest.fileUrls}
                note={`${formatMonthDay(submissionDay(latest.submittedAt))} 보냄`}
              />
            </section>
          </>
        ) : (
          <SubmissionLoadNotice load={load} onRetry={reload} />
        )}
      </div>
    </SubScreen>
  );
}

/**
 * 피그마 「제출한 초안 보기」 (서버 작업, ADR 0032 · 0038). 사장님이 확인하는 동안 낸 파일과
 * 남긴 말을 다시 본다 (GET /jobs/{id}/submissions/latest). 오른쪽 위 「신고」는 메일 문의 안내.
 */
export function StudentJobSubmittedPage({ jobId }: { jobId: number }) {
  return (
    <StageGate jobId={jobId} stage="submitted" title="제출한 결과물">
      {(job, back) => <SubmittedScreen job={job} back={back} />}
    </StageGate>
  );
}

function SubmittedScreen({ job, back }: { job: ProgressJob; back: () => void }) {
  const [reportOpen, setReportOpen] = useState(false);
  const { load, reload } = useLatestSubmission(job.jobId);
  const latest = load.status === "loaded" ? load.submission : undefined;
  const stage = (latest ? latest.submissionType === "REVISION" : job.revisionSubmitted) ? "수정안" : "초안";
  const submittedOn = latest ? submissionDay(latest.submittedAt) : job.submittedOn;
  // 「가게 · 10월 7일 제출 · 수정 1/2」. 불러오기 전에는 「가게 · 10월 7일 제출 · 수정 2회」
  const meta = progressMeta(
    job,
    submittedOn ? `${formatMonthDay(submittedOn)} 제출` : `${stage} 제출`,
    latest ? `수정 ${latest.revisionNumber}/${job.revisionLimit}` : `수정 ${job.revisionLimit}회`,
  );
  // 낸 날 + 7일까지 답이 없으면 자동으로 완료된다. 낸 날을 모르면 「7일 동안」
  const autoComplete = submittedOn ? `${formatMonthDay(addDays(submittedOn, 7))}까지` : "7일 동안";
  const note = latest?.message?.trim();

  return (
    <SubScreen
      title={`제출한 ${stage}`}
      onBack={back}
      right={
        <button type="button" className="student-work__report" onClick={() => setReportOpen(true)}>
          신고
        </button>
      }
      footer={
        <Button tone="student" fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      <div className="student-detail">
        <WorkSummary kind={job.kind} title={job.title} meta={meta} />

        <FlowBar tone="student" steps={progressFlowSteps(job, "확인 중")} />

        <div className="student-work__info">
          <strong>사장님이 확인하고 있어요</strong>
          <p>
            사장님이 수정 요청이나 완료 확인을 누르면 알려 드려요. {autoComplete} 답이 없으면 자동으로
            완료돼요.
          </p>
        </div>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">원본 파일</h2>
          {latest ? (
            <SentFiles urls={latest.fileUrls} />
          ) : (
            <SubmissionLoadNotice load={load} onRetry={reload} />
          )}
        </section>

        {note && <NoteBox title="내가 남긴 한마디" body={note} />}
      </div>

      <ReportSheet tone="student" open={reportOpen} workTitle={job.title} onClose={() => setReportOpen(false)} />
    </SubScreen>
  );
}
