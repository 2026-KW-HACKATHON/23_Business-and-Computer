import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Dialog, SubScreen } from "../components";
import { STUDENT_PATHS, useStudentWorks } from "../features/student";
import type { StudentWork } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import "./StudentPortfolioPage.css";

/** 대표 파일 확장자로 고른 회색 칸 글자 (ZIP · PDF · DOC · IMG) */
function fileBadge(work: StudentWork): string {
  const ext = work.files[0]?.name.split(".").pop()?.toLowerCase() ?? "";
  if (ext === "zip") return "ZIP";
  if (ext === "pdf") return "PDF";
  if (ext === "doc" || ext === "docx" || ext === "hwp") return "DOC";
  return "IMG";
}

/**
 * 피그마 「내 작업물 모아보기」. 사장님이 완료를 확인한 작업의 최종 결과물을 달마다 모은다.
 * 「포트폴리오 내보내기」는 확인 팝업까지 (파일로 묶기는 백엔드 연동 때).
 */
function StudentPortfolioPage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.me);
  const works = useStudentWorks()
    .filter((w) => w.status === "completed")
    .sort((a, b) => (b.completedOn ?? "").localeCompare(a.completedOn ?? ""));
  // 완료한 작업 · 함께한 가게 · 받은 평점은 아래 목록과 같은 작업에서 센다
  const storeCount = new Set(works.map((w) => w.store.id)).size;
  const ratings = works.flatMap((w) => (w.review ? [w.review.rating] : []));
  const rating =
    ratings.length > 0
      ? Math.round((ratings.reduce((sum, r) => sum + r, 0) / ratings.length) * 10) / 10
      : undefined;
  const [exportOpen, setExportOpen] = useState(false);
  const months = [...new Set(works.map((w) => (w.completedOn ?? "").slice(0, 7)))];

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
            <dd>{works.length}건</dd>
          </div>
          <div>
            <dt>함께한 가게</dt>
            <dd>{storeCount}곳</dd>
          </div>
          <div>
            <dt>받은 평점</dt>
            <dd>{rating === undefined ? "-" : `★ ${rating.toFixed(1)}`}</dd>
          </div>
        </dl>
        <p className="student-portfolio__note">
          사장님이 완료를 확인한 작업의 가장 최종 결과물만 모아 보여 줘요.
        </p>

        {works.length === 0 && <p className="student-portfolio__empty">아직 완료한 작업이 없어요</p>}
        {months.map((month) => {
          const [year, mm] = month.split("-");
          return (
            <section key={month} className="student-portfolio__month">
              <h2 className="student-portfolio__month-title">
                {year}년 {Number(mm)}월
              </h2>
              <ul className="student-portfolio__list">
                {works
                  .filter((w) => (w.completedOn ?? "").startsWith(month))
                  .map((work) => (
                    <li key={work.id}>
                      <button
                        type="button"
                        className="student-portfolio__row"
                        onClick={() => navigate(STUDENT_PATHS.workResult(work.id))}
                      >
                        <span className="student-portfolio__file" aria-hidden="true">
                          {fileBadge(work)}
                        </span>
                        <span className="student-portfolio__info">
                          <strong>{work.title}</strong>
                          <span>
                            {work.store.name}에 보냄 · {formatMonthDay(work.completedOn ?? "")}
                          </span>
                          <small>{work.files[0]?.name}</small>
                        </span>
                        <span className="student-portfolio__chevron" aria-hidden="true">
                          ›
                        </span>
                      </button>
                    </li>
                  ))}
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
