import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Dialog,
  FormField,
  LoadNotice,
  PhotoViewer,
  SubScreen,
  TextAreaField,
  WorkKindIcon,
} from "../components";
import { landingPath } from "../features/auth";
import {
  OWNER_PATHS,
  OwnerMissing,
  REQUEST_PHOTO_ACCEPT,
  addRequestPhotos,
  parsePositiveId,
  sendRevisionRequest,
  useOwnerProgressJobs,
  usePendingSubmission,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { formatMonthDay } from "../lib/date";
import { studentTitle, withSubject } from "../lib/korean";
import type { WorkKind } from "../types/workKind";
import "./OwnerRevisionPage.css";

/**
 * 피그마 「수정 요청」 + 「수정 요청 완료 팝업」. 남은 수정 횟수 안에서 고칠 곳을 한 번에 적는다.
 * 서버 작업(ADR 0035). 주소의 id 가 숫자가 아니면 찾을 수 없음.
 */
function OwnerRevisionPage() {
  const { workId = "" } = useParams();
  const back = useBack(OWNER_PATHS.home);
  const jobId = parsePositiveId(workId);
  return jobId !== undefined ? <JobRevision jobId={jobId} /> : <OwnerMissing title="수정 요청" onBack={back} />;
}

/** 작업 제목과 「학생 · 초안 도착 · 수정 n/m」 */
function RevisionWork({ kind, title, meta }: { kind: WorkKind; title: string; meta: string }) {
  return (
    <section className="owner-revision__work">
      <div className="owner-revision__work-head">
        <WorkKindIcon kind={kind} size={22} />
        <h2 className="owner-revision__work-title">{title}</h2>
      </div>
      <p className="owner-revision__meta">{meta}</p>
    </section>
  );
}

/** 고칠 곳 · 참고 사진 칸. 사진은 JPG · PNG · WEBP, 10MB 이하, 4장까지 (맞지 않는 사진은 빼고 안내) */
function RevisionForm({
  detail,
  onDetail,
  photos,
  onPhotos,
}: {
  detail: string;
  onDetail: (detail: string) => void;
  photos: File[];
  onPhotos: (photos: File[]) => void;
}) {
  const photoUrls = useObjectUrls(photos);
  const [photoNotice, setPhotoNotice] = useState<string>();
  // 크게 보는 사진의 순서. 닫혀 있으면 null
  const [viewing, setViewing] = useState<number | null>(null);
  return (
    <>
      <FormField label="자세히 적어 주세요" wrapsInput>
        <TextAreaField
          value={detail}
          maxLength={500}
          placeholder="예: 2번 게시물 사진이 어두워요. 조금 밝게 해 주세요."
          onChange={onDetail}
        />
      </FormField>

      <div className="owner-revision__photos">
        <label className="owner-revision__photo-button">
          <input
            type="file"
            accept={REQUEST_PHOTO_ACCEPT}
            multiple
            className="owner-revision__photo-input"
            onChange={(e) => {
              const picked = Array.from(e.target.files ?? []).filter(
                (file) => !photos.some((photo) => photo.name === file.name),
              );
              const next = addRequestPhotos(photos, picked);
              onPhotos(next.photos);
              setPhotoNotice(next.notice);
              e.target.value = "";
            }}
          />
          + 참고 사진 올리기 (선택)
        </label>
        {photoNotice && (
          <p className="owner-revision__note" role="status">
            {photoNotice}
          </p>
        )}
        {photos.length > 0 && (
          <ul className="owner-revision__photo-list">
            {photos.map((photo, i) => (
              <li key={photo.name}>
                <button
                  type="button"
                  className="owner-revision__thumb-button"
                  aria-label={`${photo.name} 크게 보기`}
                  onClick={() => setViewing(i)}
                >
                  <img className="owner-revision__thumb" src={photoUrls[i]} alt="" />
                </button>
                <span>{photo.name}</span>
                <button
                  type="button"
                  aria-label={`${photo.name} 빼기`}
                  onClick={() => onPhotos(photos.filter((other) => other !== photo))}
                >
                  ✕
                </button>
              </li>
            ))}
          </ul>
        )}
        {viewing !== null && viewing < photos.length && (
          <PhotoViewer
            photos={photos.map((photo, i) => ({ url: photoUrls[i], name: photo.name }))}
            index={viewing}
            onIndex={setViewing}
            onClose={() => setViewing(null)}
          />
        )}
      </div>
    </>
  );
}

/** 「수정은 N회 남았어요…」. 남은 횟수를 모르면 한 번에 적어 달라는 말만 */
function remainingText(remaining: number | undefined): string {
  if (remaining === undefined) return "한 번에 모아서 적어 주세요.";
  return remaining > 0
    ? `수정은 ${remaining}회 남았어요. 한 번에 모아서 적어 주세요.`
    : "남은 수정이 없어요. 작업 확인에서 완료를 눌러 주세요.";
}

/**
 * 서버 작업의 수정 요청 (ADR 0035). 작업은 진행 중 목록에서, 도착한 결과물은 GET /jobs/{id}/submission 에서
 * 불러온다. 참고 사진을 먼저 올리고 적은 내용과 함께 POST .../revision-request 로 보낸다. 고칠 곳은 꼭 적어야 한다.
 */
function JobRevision({ jobId }: { jobId: number }) {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.workCheck(String(jobId)));
  const { load: progressLoad, reload: reloadProgress } = useOwnerProgressJobs();
  const { load: submissionLoad, reload: reloadSubmission } = usePendingSubmission(jobId);
  const [detail, setDetail] = useState("");
  const [photos, setPhotos] = useState<File[]>([]);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
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
    return <OwnerMissing title="수정 요청" onBack={back} message="확인할 결과물이 아직 없어요" />;
  }
  if (progressLoad.status !== "loaded" || submissionLoad.status !== "loaded") {
    const failed = progressLoad.status === "error" || submissionLoad.status === "error";
    return (
      <SubScreen title="수정 요청" onBack={back}>
        <LoadNotice
          layout="page"
          status={failed ? "error" : "loading"}
          loadingText="작업을 불러오는 중이에요"
          errorText="작업을 불러오지 못했어요"
          onRetry={() => {
            if (progressLoad.status === "error") reloadProgress();
            if (submissionLoad.status === "error") reloadSubmission();
          }}
        />
      </SubScreen>
    );
  }
  const job = progressLoad.jobs.find((j) => j.jobId === jobId);
  if (!job) return <OwnerMissing title="수정 요청" onBack={back} message="진행 중인 작업이 아니에요" />;

  const submission = submissionLoad.data;
  const arrived = submission.submissionType === "REVISION" ? "수정안" : "초안";
  const who = studentTitle(submission.studentName.trim() || job.student.name || "학생");
  const limit = job.revisionLimit;
  const remaining = limit === undefined ? undefined : Math.max(0, limit - submission.revisionNumber);
  const meta = [who, `${arrived} 도착`, limit !== undefined && `수정 ${submission.revisionNumber}/${limit}`]
    .filter(Boolean)
    .join(" · ");
  const finalDue = formatMonthDay(job.finalDeadline);

  const send = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    const id = ++requestRef.current;
    setSending(true);
    setSendError(null);
    const result = await sendRevisionRequest(jobId, submission.submissionId, detail, photos);
    inFlight.current = false;
    if (id !== requestRef.current) return;
    setSending(false);
    switch (result.status) {
      case "done":
        setSent(true);
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "forbidden":
        window.alert("내 의뢰의 결과물만 확인할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "limitReached":
        setSendError("남은 수정 요청이 없어요. 작업 확인에서 완료를 눌러 주세요");
        break;
      case "photoFailed":
        setSendError("참고 사진을 올리지 못했어요. 다시 시도해 주세요");
        break;
      case "invalidInput":
        setSendError("입력한 내용을 다시 확인해 주세요");
        break;
      case "notFound":
      case "notAvailable":
      case "alreadyReviewed":
        setSendError("이미 확인했거나 끝난 작업이에요. 내 활동에서 상태를 확인해 주세요");
        break;
      default:
        setSendError("잠시 후 다시 시도해 주세요");
    }
  };

  return (
    <SubScreen
      title="수정 요청"
      onBack={back}
      footer={
        <>
          {sendError && (
            <p className="owner-revision__send-error" role="alert">
              {sendError}
            </p>
          )}
          <Button
            fullWidth
            disabled={remaining === 0 || detail.trim() === "" || sending || sent}
            onClick={() => void send()}
          >
            {sending ? "보내는 중..." : "수정 요청 보내기"}
          </Button>
        </>
      }
    >
      <div className="owner-revision">
        <RevisionWork kind={job.kind} title={submission.title || job.title} meta={meta} />

        <div className="owner-revision__intro">
          <h2 className="owner-revision__title">어떤 부분을 고칠까요?</h2>
          <p className="owner-revision__description">{remainingText(remaining)}</p>
        </div>

        <RevisionForm detail={detail} onDetail={setDetail} photos={photos} onPhotos={setPhotos} />

        <p className="owner-revision__note">학생은 최종 마감({finalDue})까지 수정본을 보내요</p>
      </div>

      <Dialog
        open={sent}
        image="doneOwner"
        title="수정 요청을 보냈어요"
        description={`${withSubject(who)} ${finalDue}까지\n수정본을 보내 드려요.`}
        actions={
          <Button
            fullWidth
            onClick={() => navigate(OWNER_PATHS.activity("inProgress"), { replace: true })}
          >
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default OwnerRevisionPage;
