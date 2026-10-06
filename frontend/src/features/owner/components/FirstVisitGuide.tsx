import { AppImage, Button } from "../../../components";
import "./FirstVisitGuide.css";

const STEPS = [
  "학생 제안이 오면 확인해요",
  "마음에 들면 맡기고 결제해요",
  "초안을 받아보고 수정을 요청해요. 수정안을 받고 완료를 누르면 그때 학생에게 작업비가 가요.",
];

interface FirstVisitGuideProps {
  /** 「첫 의뢰 올리기」 */
  onStart: () => void;
}

/**
 * 피그마 「사장님 홈 - 처음」의 안내. 백엔드가 첫 활동이라고 알려 준 계정의 홈에서
 * 할 일 목록 대신 보인다. 할 일이 하나라도 생기면 일반 홈으로 돌아간다.
 */
function FirstVisitGuide({ onStart }: FirstVisitGuideProps) {
  return (
    <section className="first-visit">
      <AppImage name="roleOwner" width={96} priority alt="" />
      <h2 className="first-visit__title">학생 제안을 기다리고 있어요</h2>
      <div className="first-visit__card">
        <h3 className="first-visit__card-title">골목인턴은 이렇게 써요</h3>
        <ol className="first-visit__steps">
          {STEPS.map((step, i) => (
            <li key={step} className="first-visit__step">
              <span className="first-visit__number" aria-hidden="true">
                {i + 1}
              </span>
              {step}
            </li>
          ))}
        </ol>
        <Button fullWidth onClick={onStart}>
          첫 의뢰 올리기
        </Button>
      </div>
    </section>
  );
}

export default FirstVisitGuide;
