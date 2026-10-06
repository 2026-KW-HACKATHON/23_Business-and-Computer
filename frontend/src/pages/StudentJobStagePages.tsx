import { useState } from "react";
import type { ReactNode } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { Button, FlowBar, LoadNotice, ReportSheet, SubScreen, TurnNotice } from "../components";
import {
  STUDENT_PATHS,
  StudentMissing,
  WorkSummary,
  progressFlowSteps,
  progressMeta,
  progressStagePath,
  useProgressJobs,
} from "../features/student";
import type { ProgressJob, ProgressStage } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
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

/** 서버가 아직 주지 않는 칸 (피그마 자리는 두고 안내만) */
function ComingSoon({ title, text }: { title?: string; text: string }) {
  return (
    <div className="student-work__quote">
      {title && <strong>{title}</strong>}
      <p>{text}</p>
    </div>
  );
}

/**
 * 피그마 「수정 요청 확인」 (서버 작업, ADR 0032). 사장님 수정 요청 내용과 내가 낸 초안 파일은
 * 서버가 아직 주지 않아 안내만 보인다. 「수정안 작성하기」 → 수정안 제출, 「문의하기」 → 채팅.
 */
export function StudentJobRevisionPage({ jobId }: { jobId: number }) {
  const navigate = useNavigate();
  return (
    <StageGate jobId={jobId} stage="revising" title="수정 요청 확인">
      {(job, back) => {
        const due = formatMonthDay(job.finalDeadline);
        const owner = job.storeName ? `${job.storeName} 사장님` : "사장님";
        return (
          <SubScreen
            title="수정 요청 확인"
            onBack={back}
            footer={
              <div className="student-detail__actions">
                <Button variant="secondary" onClick={() => navigate(STUDENT_PATHS.chats)}>
                  문의하기
                </Button>
                <Button
                  tone="student"
                  onClick={() => navigate(STUDENT_PATHS.workRevisionSubmit(String(job.jobId)))}
                >
                  수정안 작성하기
                </Button>
              </div>
            }
          >
            <div className="student-detail">
              <WorkSummary kind={job.kind} title={job.title} meta={job.storeName ? owner : ""} />

              <FlowBar tone="student" steps={progressFlowSteps(job, `${due}까지`)} />

              <TurnNotice tone="student" title={`${due}까지 수정안을 올려 주세요`} />

              <section className="student-detail__section">
                <h2 className="student-detail__section-title">{owner}의 수정 요청</h2>
                <ComingSoon text="수정 요청 내용은 곧 여기서 볼 수 있어요" />
              </section>

              <section className="student-detail__section">
                <h2 className="student-detail__section-title">
                  내가 보낸 {job.revisionSubmitted ? "수정안" : "초안"}
                </h2>
                <ComingSoon text="내가 보낸 파일은 곧 여기서 볼 수 있어요" />
              </section>
            </div>
          </SubScreen>
        );
      }}
    </StageGate>
  );
}

/**
 * 피그마 「제출한 초안 보기」 (서버 작업, ADR 0032). 사장님이 확인하는 동안 보는 화면.
 * 낸 파일과 남긴 말은 서버가 아직 학생에게 주지 않아 안내만 보인다. 오른쪽 위 「신고」는 메일 문의 안내.
 */
export function StudentJobSubmittedPage({ jobId }: { jobId: number }) {
  const [reportOpen, setReportOpen] = useState(false);
  return (
    <StageGate jobId={jobId} stage="submitted" title="제출한 결과물">
      {(job, back) => {
        const stage = job.revisionSubmitted ? "수정안" : "초안";
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
              <WorkSummary
                kind={job.kind}
                title={job.title}
                meta={progressMeta(job, `${stage} 제출`, `수정 ${job.revisionLimit}회`)}
              />

              <FlowBar tone="student" steps={progressFlowSteps(job, "확인 중")} />

              <div className="student-work__info">
                <strong>사장님이 확인하고 있어요</strong>
                <p>
                  사장님이 수정 요청이나 완료 확인을 누르면 알려 드려요. 7일 동안 답이 없으면 자동으로
                  완료돼요.
                </p>
              </div>

              <section className="student-detail__section">
                <h2 className="student-detail__section-title">원본 파일</h2>
                <ComingSoon text="내가 낸 파일과 남긴 말은 곧 여기서 볼 수 있어요" />
              </section>
            </div>

            <ReportSheet
              tone="student"
              open={reportOpen}
              workTitle={job.title}
              onClose={() => setReportOpen(false)}
            />
          </SubScreen>
        );
      }}
    </StageGate>
  );
}
