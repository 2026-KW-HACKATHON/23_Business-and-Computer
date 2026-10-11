import { useState } from "react";
import { Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import { Button, Chip, SubScreen, TextButton } from "../components";
import {
  EMPTY_JOB_FINDER_ANSWERS,
  JOB_FINDER_BUDGETS,
  JOB_FINDER_FIELDS,
  JOB_FINDER_PACE,
  JOB_FINDER_STEPS,
  STUDENT_PATHS,
  answeredSteps,
  readJobFinderAnswers,
} from "../features/student";
import type { JobFinderAnswers } from "../features/student";
import { useBack } from "../hooks/useBack";
import type { Field } from "../types/field";
import "./OwnerTaskFinderPage.css";
import "./StudentJobFinderPage.css";

const QUESTIONS = [
  { question: "어떤 일을\n해 보고 싶나요?", hint: "할 수 있는 분야를 모두 골라 주세요" },
  { question: "요즘 얼마나\n여유가 있나요?", hint: "첫 결과물(초안)을 보낼 때까지 필요한 시간이에요" },
  { question: "작업비는\n어느 정도면 좋을까요?", hint: "의뢰 한 건에 받는 작업비예요" },
] as const;

/**
 * 피그마 「나에게 맞는 일 찾기 - 질문」 (ADR 0072). 사장님 「맡길 일 찾기」 질문 화면과 같은 틀에 학생 색.
 * 1번은 분야 칩을 여러 개(또는 「아무 일이나 좋아요」) 고르고 「다음」, 2 · 3번은 답을 누르면 바로 다음으로 간다.
 * 앱바 오른쪽 「건너뛰기」는 남은 질문을 넘기고 바로 결과로. 답은 화면 기록(state)에만 들고 다녀
 * ← 로 앞 질문에 돌아가면 그때의 답이 남는다. 앞 질문에 답하지 않고 들어왔으면 1번부터.
 */
function StudentJobFinderPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { step } = useParams();
  const back = useBack(STUDENT_PATHS.home);
  const answers = readJobFinderAnswers(location.state) ?? EMPTY_JOB_FINDER_ANSWERS;
  const index = Number(step) - 1;
  const [fields, setFields] = useState<Field[] | null>(null);

  if (!QUESTIONS[index] || index !== answeredSteps(answers)) {
    return <Navigate to={STUDENT_PATHS.jobFinder(1)} replace state={{ jobFinder: EMPTY_JOB_FINDER_ANSWERS }} />;
  }

  const go = (next: JobFinderAnswers) => {
    navigate(index + 1 === JOB_FINDER_STEPS ? STUDENT_PATHS.jobFinderResult : STUDENT_PATHS.jobFinder(index + 2), {
      state: { jobFinder: next },
    });
  };
  const skipToResult = () => navigate(STUDENT_PATHS.jobFinderResult, { state: { jobFinder: answers } });

  const toggleField = (field: Field) =>
    setFields((now) => {
      const list = now ?? [];
      return list.includes(field) ? list.filter((f) => f !== field) : [...list, field];
    });
  const any = fields !== null && fields.length === 0;

  return (
    <SubScreen
      title="나에게 맞는 일 찾기"
      onBack={back}
      right={
        <TextButton showChevron={false} onClick={skipToResult}>
          건너뛰기
        </TextButton>
      }
      footer={
        index === 0 && (
          <Button
            tone="student"
            fullWidth
            disabled={fields === null}
            onClick={() => go({ ...answers, fields: fields ?? [] })}
          >
            다음
          </Button>
        )
      }
    >
      <div className="task-finder task-finder--student">
        <div className="task-finder__progress">
          <div
            className="task-finder__track"
            role="progressbar"
            aria-valuemin={1}
            aria-valuemax={JOB_FINDER_STEPS}
            aria-valuenow={index + 1}
            aria-label="질문 진행"
          >
            <span style={{ width: `${((index + 1) / JOB_FINDER_STEPS) * 100}%` }} />
          </div>
          <span className="task-finder__count">
            {index + 1}/{JOB_FINDER_STEPS}
          </span>
        </div>

        <div className="task-finder__intro">
          <h2 className="task-finder__question">{QUESTIONS[index].question}</h2>
          <p className="task-finder__hint">{QUESTIONS[index].hint}</p>
        </div>

        {index === 0 ? (
          <div className="job-finder__fields" role="group" aria-label="할 수 있는 분야">
            {JOB_FINDER_FIELDS.map((field) => (
              <Chip
                key={field}
                variant="outlined"
                tone="student"
                label={field}
                selected={fields?.includes(field) ?? false}
                onClick={() => toggleField(field)}
              />
            ))}
            <Chip
              variant="outlined"
              tone="student"
              label="아무 일이나 좋아요"
              selected={any}
              onClick={() => setFields(any ? null : [])}
            />
          </div>
        ) : (
          <div className="task-finder__answers">
            {(index === 1 ? JOB_FINDER_PACE : JOB_FINDER_BUDGETS).map(({ label }, choice) => (
              <button
                key={label}
                type="button"
                className="task-finder__answer"
                onClick={() => go(index === 1 ? { ...answers, pace: choice } : { ...answers, budget: choice })}
              >
                {label}
              </button>
            ))}
          </div>
        )}
      </div>
    </SubScreen>
  );
}

export default StudentJobFinderPage;
