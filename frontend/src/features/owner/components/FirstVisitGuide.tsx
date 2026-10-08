import { TodoStartCard } from "../../../components";

interface FirstVisitGuideProps {
  /** 「첫 의뢰 올리기」 */
  onStart: () => void;
}

/**
 * 피그마 「사장님 홈 - 처음」의 확인할 일 카드 (현재=처음, ADR 0051). 모집 중 · 받은 제안 · 진행 중 · 끝난 의뢰가
 * 모두 비어 있는 계정의 홈에서 할 일 카드 자리에 보인다. 하나라도 생기면 일반 홈으로 돌아간다
 */
function FirstVisitGuide({ onStart }: FirstVisitGuideProps) {
  return (
    <TodoStartCard
      tone="owner"
      title={"첫 의뢰를\n올려 볼까요?"}
      description={"의뢰나 제안이 생기면\n여기서 확인해요"}
      steps={[
        <>
          <strong>의뢰</strong>를 올리거나 <strong>학생 제안</strong>을 받아요
        </>,
        <>
          마음에 드는 학생에게 <strong>맡기고 결제</strong>해요
        </>,
        <>
          <strong>초안</strong>과 <strong>수정안</strong>을 받고 <strong>완료</strong>를 누르면
          <br />
          작업비가 학생에게 가요
        </>,
      ]}
      actionLabel="첫 의뢰 올리기"
      onAction={onStart}
    />
  );
}

export default FirstVisitGuide;
