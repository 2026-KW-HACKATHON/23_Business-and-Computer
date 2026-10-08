import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { downloadSubmissionZip, saveFile } from "../api/submissionDownload";
import type { SubmissionDownloadFailure } from "../api/submissionDownload";
import { BottomSheet, Button, Checkbox, LoadNotice, LoadingDots, SubScreen } from "../components";
import { STUDENT_PATHS, useFinishedJobs } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, todayIsoDate } from "../lib/date";
import { fileNameFromUrl } from "../lib/fileUrl";
import "./StudentPortfolioPage.css";

/** 대표 파일 확장자로 고른 회색 칸 글자 (ZIP · PDF · DOC · IMG) */
function fileBadge(fileName: string | undefined): string {
  const ext = fileName?.split(".").pop()?.toLowerCase() ?? "";
  if (ext === "zip") return "ZIP";
  if (ext === "pdf") return "PDF";
  if (ext === "doc" || ext === "docx" || ext === "hwp") return "DOC";
  return "IMG";
}

/** ZIP 을 받지 못했을 때 시트 아래 안내 */
const EXPORT_FAILURE_TEXT: Record<Exclude<SubmissionDownloadFailure, "unauthorized">, string> = {
  busy: "지금 내려받는 사람이 많아요. 잠시 후 다시 시도해 주세요",
  missing: "찾을 수 없는 파일이 있어요. 목록을 새로 고친 뒤 다시 골라 주세요",
  failed: "ZIP을 만들지 못했어요. 잠시 후 다시 시도해 주세요",
};

/**
 * 피그마 「내 작업물 모아보기」. 사장님이 완료를 확인한 작업의 최종 결과물을 달마다 모은다.
 * 완료한 작업은 정산 내역(GET /settlements), 파일은 GET /jobs/{id}/result, 평점은 받은 후기 (ADR 0042).
 * 「포트폴리오 내보내기」는 시트에서 결과물을 고르거나 전체 선택해, 고른 작업의 최종 결과물 파일을
 * POST /jobs/submissions/download 로 ZIP 하나로 받아 저장한다.
 */
function StudentPortfolioPage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.me);
  const { load, reload } = useFinishedJobs({ reviews: true, files: true });
  const [exportOpen, setExportOpen] = useState(false);
  const [selected, setSelected] = useState<ReadonlySet<number>>(new Set());
  const [saving, setSaving] = useState(false);
  const [exportNotice, setExportNotice] = useState("");
  const loaded = load.status === "loaded";
  const works = loaded ? load.jobs.filter((job) => job.outcome === "completed") : [];
  // 내보낼 수 있는 작업: 최종 결과물 파일을 불러온 것
  const exportable = works.filter((work) => (work.fileUrls?.length ?? 0) > 0);
  const picked = exportable.filter((work) => selected.has(work.jobId));
  const allPicked = exportable.length > 0 && picked.length === exportable.length;
  // 완료한 작업 · 함께한 가게 · 받은 평점은 아래 목록과 같은 작업에서 센다
  const storeCount = new Set(works.flatMap((w) => (w.storeName ? [w.storeName] : []))).size;
  const ratings = works.flatMap((w) => (w.rating !== undefined ? [w.rating] : []));
  const rating =
    ratings.length > 0
      ? Math.round((ratings.reduce((sum, r) => sum + r, 0) / ratings.length) * 10) / 10
      : undefined;
  const months = [...new Set(works.map((w) => (w.closedOn ?? "").slice(0, 7)))];

  const openExport = () => {
    setExportNotice("");
    setExportOpen(true);
  };

  const pick = (jobId: number, on: boolean) => {
    const next = new Set(selected);
    if (on) next.add(jobId);
    else next.delete(jobId);
    setSelected(next);
  };

  const pickAll = (on: boolean) => setSelected(on ? new Set(exportable.map((work) => work.jobId)) : new Set());

  const saveZip = async () => {
    setSaving(true);
    setExportNotice("");
    const result = await downloadSubmissionZip(
      picked.map((work) => ({ jobId: work.jobId, fileUrls: work.fileUrls ?? [] })),
    );
    setSaving(false);
    if (result.status === "downloaded") {
      saveFile(result.zip, `골목인턴_포트폴리오_${todayIsoDate()}.zip`);
      setExportOpen(false);
      setSelected(new Set());
      return;
    }
    if (result.reason === "unauthorized") {
      navigate("/login", { replace: true });
      return;
    }
    setExportNotice(EXPORT_FAILURE_TEXT[result.reason]);
  };

  return (
    <SubScreen
      title="내 작업물 모아보기"
      onBack={back}
      footer={
        works.length > 0 && (
          <Button tone="student" fullWidth onClick={openExport}>
            포트폴리오 내보내기
          </Button>
        )
      }
    >
      <div className="student-portfolio">
        <dl className="student-portfolio__stats">
          <div>
            <dt>완료한 작업</dt>
            <dd>{loaded ? `${works.length}건` : <LoadingDots label="완료한 작업 불러오는 중" />}</dd>
          </div>
          <div>
            <dt>함께한 가게</dt>
            <dd>{loaded ? `${storeCount}곳` : <LoadingDots label="함께한 가게 불러오는 중" />}</dd>
          </div>
          <div>
            <dt>받은 평점</dt>
            <dd>{rating === undefined ? "-" : `★ ${rating.toFixed(1)}`}</dd>
          </div>
        </dl>
        <p className="student-portfolio__note">
          사장님이 완료를 확인한 작업의 가장 최종 결과물만 모아 보여 줘요.
        </p>

        {!loaded && (
          <LoadNotice
            status={load.status}
            loadingText="내 작업물을 불러오는 중이에요"
            errorText="내 작업물을 불러오지 못했어요"
            onRetry={reload}
          />
        )}
        {loaded && works.length === 0 && <p className="student-portfolio__empty">아직 완료한 작업이 없어요</p>}
        {months.map((month) => {
          const [year, mm] = month.split("-");
          return (
            <section key={month} className="student-portfolio__month">
              <h2 className="student-portfolio__month-title">
                {year}년 {Number(mm)}월
              </h2>
              <ul className="student-portfolio__list">
                {works
                  .filter((w) => (w.closedOn ?? "").startsWith(month))
                  .map((work) => {
                    const firstFile = work.fileUrls?.[0] ? fileNameFromUrl(work.fileUrls[0]) : undefined;
                    return (
                      <li key={work.jobId}>
                        <button
                          type="button"
                          className="student-portfolio__row"
                          onClick={() => navigate(STUDENT_PATHS.workResult(String(work.jobId)))}
                        >
                          <span className="student-portfolio__file" aria-hidden="true">
                            {fileBadge(firstFile)}
                          </span>
                          <span className="student-portfolio__info">
                            <strong>{work.title}</strong>
                            <span>
                              {[
                                work.storeName && `${work.storeName}에 보냄`,
                                work.closedOn && formatMonthDay(work.closedOn),
                              ]
                                .filter(Boolean)
                                .join(" · ")}
                            </span>
                            {firstFile && <small>{firstFile}</small>}
                          </span>
                          <span className="student-portfolio__chevron" aria-hidden="true">
                            ›
                          </span>
                        </button>
                      </li>
                    );
                  })}
              </ul>
            </section>
          );
        })}
      </div>

      <BottomSheet
        open={exportOpen}
        onClose={() => setExportOpen(false)}
        title="포트폴리오 내보내기"
        description="ZIP으로 저장할 결과물을 골라 주세요"
        footer={
          <div className="student-portfolio__export-footer">
            {exportNotice && (
              <p className="student-portfolio__export-notice" role="alert">
                {exportNotice}
              </p>
            )}
            <Button
              tone="student"
              fullWidth
              disabled={picked.length === 0}
              loading={saving}
              loadingLabel="ZIP을 만드는 중"
              onClick={() => void saveZip()}
            >
              {picked.length > 0 ? `${picked.length}건 ZIP으로 저장하기` : "ZIP으로 저장하기"}
            </Button>
          </div>
        }
      >
        {exportable.length === 0 ? (
          <p className="student-portfolio__export-empty">내보낼 결과물 파일이 없어요</p>
        ) : (
          <div className="student-portfolio__export">
            <div className="student-portfolio__export-all">
              <Checkbox checked={allPicked} onChange={pickAll} label={`전체 선택 (${exportable.length}건)`} />
            </div>
            <ul className="student-portfolio__export-list">
              {exportable.map((work) => (
                <li key={work.jobId}>
                  <Checkbox
                    checked={selected.has(work.jobId)}
                    onChange={(on) => pick(work.jobId, on)}
                    label={work.title}
                    description={[
                      work.storeName,
                      work.closedOn && formatMonthDay(work.closedOn),
                      `파일 ${work.fileUrls?.length ?? 0}개`,
                    ]
                      .filter(Boolean)
                      .join(" · ")}
                  />
                </li>
              ))}
            </ul>
          </div>
        )}
      </BottomSheet>
    </SubScreen>
  );
}

export default StudentPortfolioPage;
