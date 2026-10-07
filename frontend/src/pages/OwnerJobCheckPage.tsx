import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  FlowBar,
  LoadNotice,
  NoteBox,
  ReportSheet,
  SubScreen,
  TurnNotice,
  WorkKindIcon,
} from "../components";
import { landingPath } from "../features/auth";
import {
  OWNER_PATHS,
  OwnerMissing,
  flowSteps,
  ownerAutoCompleteOn,
  sendSubmissionComplete,
  submissionFileName,
  useOwnerProgressJobs,
  usePendingSubmission,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { studentTitle } from "../lib/korean";
import "./OwnerDetailPage.css";
import "./OwnerWorkCheckPage.css";

/**
 * 피그마 「작업 확인 · 초안」 · 「작업 확인 · 수정안」 (서버 작업, ADR 0035).
 * 작업은 진행 중 목록(GET /me/jobs?status=MATCHED)에서, 도착한 결과물은 GET /jobs/{id}/submission 에서 불러온다.
 * 「수정 요청」 → 수정 요청 화면, 「완료 확인」 → POST .../complete 뒤 후기 작성 (ADR 0036).
 * 남은 수정이 없으면 「수정 요청」 버튼이 없다. 확인할 날짜는 목록의 도착 시각 + 7일, 모르면 「7일 동안」.
 */
function OwnerJobCheckPage({ jobId }: { jobId: number }) {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const { load: progressLoad, reload: reloadProgress } = useOwnerProgressJobs();
  const { load: submissionLoad, reload: reloadSubmission } = usePendingSubmission(jobId);
  const [reportOpen, setReportOpen] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [completeError, setCompleteError] = useState<string | null>(null);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 보낸다
  const inFlight = useRef(false);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestRef = useRef(0);

  useEffect(() => {
    const latest = requestRef;
    return () => {
      latest.current += 1;
    };
  }, []);

  if (submissionLoad.status === "notFound" || submissionLoad.status === "closed") {
    return <OwnerMissing title="작업 확인" onBack={back} message="확인할 결과물이 아직 없어요" />;
  }
  if (progressLoad.status !== "loaded" || submissionLoad.status !== "loaded") {
    const failed = progressLoad.status === "error" || submissionLoad.status === "error";
    return (
      <SubScreen title="작업 확인" onBack={back}>
        <LoadNotice
          status={failed ? "error" : "loading"}
          loadingText="결과물을 불러오는 중이에요"
          errorText="결과물을 불러오지 못했어요"
          onRetry={() => {
            if (progressLoad.status === "error") reloadProgress();
            if (submissionLoad.status === "error") reloadSubmission();
          }}
        />
      </SubScreen>
    );
  }
  const job = progressLoad.jobs.find((j) => j.jobId === jobId);
  if (!job) return <OwnerMissing title="작업 확인" onBack={back} message="진행 중인 작업이 아니에요" />;

  const submission = submissionLoad.data;
  const noun = submission.submissionType === "REVISION" ? "수정안" : "초안";
  const who = studentTitle(submission.studentName.trim() || job.student.name || "학생");
  const limit = job.revisionLimit;
  // 초안은 0, 수정안은 1부터. 수정 횟수를 모르면 버튼을 두고 서버 답으로 막는다
  const remaining = limit === undefined ? undefined : Math.max(0, limit - submission.revisionNumber);
  // 도착 날짜를 알면 「초안 도착 9월 22일」 · 「9월 29일까지 확인해 주세요」, 모르면 「7일 동안」
  const autoCompleteOn = ownerAutoCompleteOn(job);
  const arrived = job.arrivedOn ? `${noun} 도착 ${formatMonthDay(job.arrivedOn)}` : `${noun} 도착`;
  const meta = [who, arrived, limit !== undefined && `수정 ${submission.revisionNumber}/${limit}`]
    .filter(Boolean)
    .join(" · ");
  const notice = [
    autoCompleteOn ? "답이 없으면 자동으로 완료돼요" : "7일 동안 답이 없으면 자동으로 완료돼요",
    remaining === undefined ? "" : remaining > 0 ? `수정 요청 ${remaining}회 남음` : "남은 수정 요청이 없어요",
  ]
    .filter(Boolean)
    .join("\n");

  const complete = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    const id = ++requestRef.current;
    setCompleting(true);
    setCompleteError(null);
    const result = await sendSubmissionComplete(jobId, submission.submissionId);
    inFlight.current = false;
    if (id !== requestRef.current) return;
    setCompleting(false);
    switch (result.status) {
      case "done":
        navigate(OWNER_PATHS.workReview(String(jobId)), { replace: true });
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "forbidden":
        window.alert("내 의뢰의 결과물만 확인할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "notFound":
      case "notAvailable":
      case "alreadyReviewed":
        setCompleteError("이미 확인했거나 끝난 작업이에요. 내 활동에서 상태를 확인해 주세요");
        break;
      default:
        setCompleteError("잠시 후 다시 시도해 주세요");
    }
  };

  return (
    <SubScreen
      title={`${noun} 확인`}
      onBack={back}
      right={
        <button type="button" className="owner-check__report" onClick={() => setReportOpen(true)}>
          신고
        </button>
      }
      footer={
        <>
          {completeError && (
            <p className="owner-check__send-error" role="alert">
              {completeError}
            </p>
          )}
          <div className="owner-detail__actions">
            {remaining !== 0 && (
              <Button
                variant="secondary"
                disabled={completing}
                onClick={() => navigate(OWNER_PATHS.workRevision(String(jobId)))}
              >
                수정 요청
              </Button>
            )}
            <Button disabled={completing} onClick={() => void complete()}>
              {completing ? "완료하는 중..." : "완료 확인"}
            </Button>
          </div>
        </>
      }
    >
      <div className="owner-detail">
        <div className="owner-detail__work">
          <div className="owner-detail__work-head">
            <WorkKindIcon kind={job.kind} size={22} />
            <h2 className="owner-detail__work-title">{submission.title || job.title}</h2>
          </div>
          <p className="owner-detail__work-meta">{meta}</p>
        </div>

        <FlowBar
          steps={flowSteps(job.kind === "proposal" ? "제안" : "의뢰", noun === "수정안" ? 3 : 2, "확인해 주세요")}
        />

        <div className="owner-check__notice">
          <h2 className="owner-check__headline">{noun}이 도착했어요</h2>
          <TurnNotice
            tone="owner"
            title={autoCompleteOn ? `${formatMonthDay(autoCompleteOn)}까지 확인해 주세요` : `${noun}을 확인해 주세요`}
            description={notice}
          />
        </div>

        <section className="owner-detail__section owner-check__files">
          <h2 className="owner-detail__section-title">원본 파일</h2>
          <ul className="owner-check__file-list">
            {submission.fileUrls.map((url) => (
              <li key={url} className="owner-check__file">
                <span className="owner-check__file-name">{submissionFileName(url)}</span>
                <a className="text-button owner-check__download" href={url} target="_blank" rel="noreferrer" download>
                  <span>받기</span>
                </a>
              </li>
            ))}
          </ul>
        </section>

        {submission.message.trim() && <NoteBox title={`${who}의 메세지`} body={submission.message} />}
      </div>

      <ReportSheet open={reportOpen} workTitle={job.title} onClose={() => setReportOpen(false)} />
    </SubScreen>
  );
}

export default OwnerJobCheckPage;
