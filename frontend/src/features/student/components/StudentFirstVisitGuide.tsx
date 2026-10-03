import { AppImage, Button } from "../../../components";
import "./StudentFirstVisitGuide.css";

const STEPS = [
  "자주 가는 가게에 개선안을 보내거나 올라온 의뢰에 지원해요",
  "사장님이 받아들이면 작업이 시작돼요",
  "초안을 내고, 사장님께 수정 요청을 받으면 수정안도 제출해요. 완료되면 작업비가 정산돼요.",
];

interface StudentFirstVisitGuideProps {
  /** 「첫 제안 쓰기」 */
  onStart: () => void;
}

/**
 * 피그마 「학생 홈 - 처음」의 안내. 작업 · 지원 · 제안이 하나도 없는 계정의 홈에서
 * 할 일 목록 대신 보인다. 하나라도 생기면 일반 홈으로 돌아간다.
 */
function StudentFirstVisitGuide({ onStart }: StudentFirstVisitGuideProps) {
  return (
    <section className="student-first-visit">
      <AppImage name="roleStudent" width={96} priority alt="" />
      <h2 className="student-first-visit__title">{"월계1동 가게에\n첫 제안을 보내 볼까요?"}</h2>
      <div className="student-first-visit__card">
        <h3 className="student-first-visit__card-title">가꿈은 이렇게 써요</h3>
        <ol className="student-first-visit__steps">
          {STEPS.map((step, i) => (
            <li key={step} className="student-first-visit__step">
              <span className="student-first-visit__number" aria-hidden="true">
                {i + 1}
              </span>
              {step}
            </li>
          ))}
        </ol>
        <Button tone="student" fullWidth onClick={onStart}>
          첫 제안 쓰기
        </Button>
      </div>
    </section>
  );
}

export default StudentFirstVisitGuide;
