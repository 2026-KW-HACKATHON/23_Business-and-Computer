import { Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import { SubScreen } from "../components";
import { OWNER_PATHS, TASK_FINDER_QUESTIONS, readTaskFinderAnswers } from "../features/owner";
import { useBack } from "../hooks/useBack";
import "./OwnerTaskFinderPage.css";

/**
 * 피그마 「맡길 일 찾기 - 질문」 (ADR 0067). 한 화면에 질문 하나, 답을 누르면 바로 다음 질문으로 간다.
 * 답은 서버에 저장하지 않고 화면 기록(state)에 들고 다녀서, ← 로 앞 질문에 돌아가면 그때의 답으로 돌아간다.
 * 앞 질문에 답하지 않고 들어왔으면(주소로 바로 등) 첫 질문부터. 마지막 질문에 답하면 결과로.
 */
function OwnerTaskFinderPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { step } = useParams();
  const back = useBack(OWNER_PATHS.home);
  const answers = readTaskFinderAnswers(location.state) ?? [];
  const total = TASK_FINDER_QUESTIONS.length;
  const index = Number(step) - 1;
  const question = TASK_FINDER_QUESTIONS[index];

  if (!question || index !== answers.length) return <Navigate to={OWNER_PATHS.taskFinder(1)} replace />;

  const answer = (choice: 0 | 1) => {
    const next = [...answers, choice];
    navigate(next.length === total ? OWNER_PATHS.taskFinderResult : OWNER_PATHS.taskFinder(index + 2), {
      state: { answers: next },
    });
  };

  return (
    <SubScreen title="맡길 일 찾기" onBack={back}>
      <div className="task-finder">
        <div className="task-finder__progress">
          <div
            className="task-finder__track"
            role="progressbar"
            aria-valuemin={1}
            aria-valuemax={total}
            aria-valuenow={index + 1}
            aria-label="질문 진행"
          >
            <span style={{ width: `${((index + 1) / total) * 100}%` }} />
          </div>
          <span className="task-finder__count">
            {index + 1}/{total}
          </span>
        </div>

        <div className="task-finder__intro">
          <h2 className="task-finder__question">{question.question}</h2>
          <p className="task-finder__hint">가게 상황에 가까운 답을 골라 주세요</p>
        </div>

        <div className="task-finder__answers">
          {question.answers.map((label, choice) => (
            <button
              key={label}
              type="button"
              className="task-finder__answer"
              onClick={() => answer(choice as 0 | 1)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>
    </SubScreen>
  );
}

export default OwnerTaskFinderPage;
