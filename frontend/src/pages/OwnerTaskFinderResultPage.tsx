import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Button, CategoryBadge, SubScreen } from "../components";
import { OWNER_PATHS, TASK_FINDER_QUESTIONS, readTaskFinderAnswers, taskFinderPicks } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import "./OwnerTaskFinderPage.css";

/**
 * 피그마 「맡길 일 찾기 - 결과」 · 「결과 (해당 없음)」 (ADR 0067).
 * 추천 답이 나온 질문 중 위 순서대로 3개까지 카드로 보이고, 제목의 개수는 카드 수를 따른다.
 * 하나도 없으면 「이런 일도 맡겨 볼 수 있어요」.
 * 카드를 누르면 홈 「이런 의뢰는 어때요?」처럼 그 예시로 채운 의뢰 등록 1/3 으로 간다 (B-2).
 * ← 는 마지막 질문으로, 「다시 점검하기」는 첫 질문부터, 「홈으로」는 사장님 홈.
 */
function OwnerTaskFinderResultPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(OWNER_PATHS.home);
  const answers = readTaskFinderAnswers(location.state);

  if (!answers || answers.length !== TASK_FINDER_QUESTIONS.length) {
    return <Navigate to={OWNER_PATHS.taskFinder(1)} replace />;
  }
  const { matched, picks } = taskFinderPicks(answers);

  return (
    <SubScreen
      title="맡길 일 찾기"
      onBack={back}
      footer={
        <div className="task-finder__footer">
          <Button variant="secondary" fullWidth onClick={() => navigate(OWNER_PATHS.taskFinder(1))}>
            다시 점검하기
          </Button>
          <Button fullWidth onClick={() => navigate(OWNER_PATHS.home)}>
            홈으로
          </Button>
        </div>
      }
    >
      <div className="task-finder task-finder--result">
        <div className="task-finder__intro">
          <h2 className="task-finder__question">
            {matched ? `지금 맡기면 좋은 일 ${picks.length}가지` : "이런 일도 맡겨 볼 수 있어요"}
          </h2>
          <p className="task-finder__hint">
            {matched
              ? "누르면 의뢰서가 채워져서 바로 시작할 수 있어요"
              : "지금도 가게를 잘 챙기고 계세요. 손님을 더 모으고 싶다면 이런 일부터 시작해 보세요"}
          </p>
        </div>

        <ul className="task-finder__picks">
          {picks.map(({ example, cardTitle, reason }) => (
            <li key={example.id}>
              <button
                type="button"
                className="task-finder__pick"
                onClick={() => navigate(OWNER_PATHS.newRequest, { state: { exampleId: example.id } })}
              >
                <CategoryBadge field={example.field} />
                <strong className="task-finder__pick-title">{cardTitle}</strong>
                <span className="task-finder__pick-reason">{reason}</span>
                <span className="task-finder__pick-foot">
                  <span>예시 작업비 {formatWon(example.content.budget)}</span>
                  <span className="task-finder__pick-link">이 일로 의뢰하기 ›</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </SubScreen>
  );
}

export default OwnerTaskFinderResultPage;
