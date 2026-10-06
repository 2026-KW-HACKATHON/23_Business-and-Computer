import { useNavigate } from "react-router-dom";
import { SubScreen } from "../components";
import {
  STUDENT_PATHS,
  SettlementSummaryBox,
  useStudentSettlements,
  useStudentWorks,
} from "../features/student";
import type { StudentSettlement } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, todayIsoDate } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentSettlementsPage.css";

const STATUS_LABEL: Record<StudentSettlement["status"], string> = {
  expected: "정산 예정",
  settled: "정산 완료",
  reward: "착수 보상",
};

/** 「공룡카페 · 9월 12일 정산」 · 「치킨플러스 · 작업 중」 */
function detailOf(s: StudentSettlement): string {
  if (s.status === "expected") return `${s.storeName} · 작업 중`;
  if (s.status === "reward") return `${s.storeName} · ${formatMonthDay(s.date)} 사장님 사정 취소`;
  return `${s.storeName} · ${formatMonthDay(s.date)} ${s.autoCompleted ? "자동 완료 " : ""}정산`;
}

/** 피그마 「정산 내역 (학생)」. 달마다 묶고, 정산 예정 · 정산 완료 · 착수 보상을 칩으로 보여 준다 */
function StudentSettlementsPage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.me);
  const { settlements, summary } = useStudentSettlements();
  const works = useStudentWorks();
  // 정산 예정은 이번 달에 받을 돈이라 이번 달 묶음에 넣는다
  const thisMonth = todayIsoDate().slice(0, 7);
  const monthOf = (s: StudentSettlement) => (s.status === "expected" ? thisMonth : s.date.slice(0, 7));
  const months = [...new Set(settlements.map(monthOf))].sort((a, b) => b.localeCompare(a));

  // 작업 중 → 지금 할 일 화면, 정산 완료 → 내 결과물, 착수 보상 → 성사되지 않은 작업
  const open = (s: StudentSettlement) => {
    if (s.status === "settled") return navigate(STUDENT_PATHS.workResult(s.workId));
    if (s.status === "reward") return navigate(STUDENT_PATHS.workCanceled(s.workId));
    const work = works.find((w) => w.id === s.workId);
    if (work?.status === "revising") return navigate(STUDENT_PATHS.workRevision(s.workId));
    if (work?.status === "submitted") return navigate(STUDENT_PATHS.workSubmitted(s.workId));
    return navigate(STUDENT_PATHS.workSubmit(s.workId));
  };

  return (
    <SubScreen title="정산 내역" onBack={back}>
      <div className="student-settlements">
        <SettlementSummaryBox summary={summary} />
        <p className="student-settlements__note">
          사장님이 완료를 확인하면 작업비가 정산돼요. 해커톤 기간에는 수수료가 없어요.
        </p>

        {months.map((month) => {
          const [year, mm] = month.split("-");
          return (
            <section key={month} className="student-settlements__month">
              <h2 className="student-settlements__month-title">
                {year}년 {Number(mm)}월
              </h2>
              <ul className="student-settlements__list">
                {settlements
                  .filter((s) => monthOf(s) === month)
                  .map((s) => (
                    <li key={s.workId}>
                      <button type="button" className="student-settlements__row" onClick={() => open(s)}>
                        <span className="student-settlements__info">
                          <span className="student-settlements__title">{s.title}</span>
                          <span className="student-settlements__detail">{detailOf(s)}</span>
                        </span>
                        <span className="student-settlements__side">
                          <span className="student-settlements__amount">{formatWon(s.amount)}</span>
                          <span
                            className={`student-settlements__status student-settlements__status--${s.status}`}
                          >
                            {STATUS_LABEL[s.status]}
                          </span>
                        </span>
                      </button>
                    </li>
                  ))}
              </ul>
            </section>
          );
        })}
      </div>
    </SubScreen>
  );
}

export default StudentSettlementsPage;
