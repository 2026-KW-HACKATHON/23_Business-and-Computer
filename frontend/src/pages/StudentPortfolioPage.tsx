import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Dialog, LoadNotice, SubScreen } from "../components";
import { STUDENT_PATHS, useFinishedJobs } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
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

/**
 * 피그마 「내 작업물 모아보기」. 사장님이 완료를 확인한 작업의 최종 결과물을 달마다 모은다.
 * 완료한 작업은 정산 내역(GET /settlements), 파일은 GET /jobs/{id}/result, 평점은 받은 후기 (ADR 0042).
 * 「포트폴리오 내보내기」는 확인 팝업까지 (파일로 묶기는 백엔드 연동 때).
 */
function StudentPortfolioPage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.me);
  const { load, reload } = useFinishedJobs({ reviews: true, files: true });
  const [exportOpen, setExportOpen] = useState(false);
  const loaded = load.status === "loaded";
  const works = loaded ? load.jobs.filter((job) => job.outcome === "completed") : [];
  // 완료한 작업 · 함께한 가게 · 받은 평점은 아래 목록과 같은 작업에서 센다
  const storeCount = new Set(works.flatMap((w) => (w.storeName ? [w.storeName] : []))).size;
  const ratings = works.flatMap((w) => (w.rating !== undefined ? [w.rating] : []));
  const rating =
    ratings.length > 0
      ? Math.round((ratings.reduce((sum, r) => sum + r, 0) / ratings.length) * 10) / 10
      : undefined;
  const months = [...new Set(works.map((w) => (w.closedOn ?? "").slice(0, 7)))];

  return (
    <SubScreen
      title="내 작업물 모아보기"
      onBack={back}
      footer={
        works.length > 0 && (
          <Button tone="student" fullWidth onClick={() => setExportOpen(true)}>
            포트폴리오 내보내기
          </Button>
        )
      }
    >
      <div className="student-portfolio">
        <dl className="student-portfolio__stats">
          <div>
            <dt>완료한 작업</dt>
            <dd>{loaded ? `${works.length}건` : "-"}</dd>
          </div>
          <div>
            <dt>함께한 가게</dt>
            <dd>{loaded ? `${storeCount}곳` : "-"}</dd>
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

      <Dialog
        open={exportOpen}
        title="포트폴리오를 내보낼까요?"
        description={`완료한 작업 ${works.length}건의 최종 결과물을\n가게·날짜와 함께 한 파일로 모아 드려요.`}
        onClose={() => setExportOpen(false)}
        actions={
          <>
            {/* 파일 묶기는 백엔드 연동 때 붙인다 */}
            <Button tone="student" fullWidth onClick={() => setExportOpen(false)}>
              ZIP으로 저장하기
            </Button>
            <Button variant="secondary" fullWidth onClick={() => setExportOpen(false)}>
              돌아가기
            </Button>
          </>
        }
      />
    </SubScreen>
  );
}

export default StudentPortfolioPage;
