import { TodoStartCard } from "../../../components";

interface StudentFirstVisitGuideProps {
  /** 「첫 제안 쓰기」 */
  onStart: () => void;
}

/**
 * 피그마 「학생 홈 - 처음」의 확인할 일 카드 (현재=처음, ADR 0051). 작업 · 지원 · 제안이 하나도 없는 계정의
 * 홈에서 할 일 카드 자리에 보인다. 하나라도 생기면 일반 홈으로 돌아간다
 */
function StudentFirstVisitGuide({ onStart }: StudentFirstVisitGuideProps) {
  return (
    <TodoStartCard
      tone="student"
      title={"첫 제안을\n보내 볼까요?"}
      description={"제안이나 지원이 생기면\n여기서 확인해요"}
      steps={[
        <>
          가게에 <strong>제안</strong>을 보내거나 <strong>의뢰에 지원</strong>해요
        </>,
        <>
          사장님이 받아들이면 <strong>작업을 시작</strong>해요
        </>,
        <>
          <strong>초안</strong>과 <strong>수정안</strong>을 내고 <strong>완료</strong>되면
          <br />
          작업비를 받아요
        </>,
      ]}
      actionLabel="첫 제안 쓰기"
      onAction={onStart}
    />
  );
}

export default StudentFirstVisitGuide;
