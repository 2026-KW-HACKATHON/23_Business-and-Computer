import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppBar, AppImage, Button, PageDots, preloadImages } from "../components";
import type { ImageName } from "../components";
import { markOnboardingSeen } from "../features/onboarding";
import "./OnboardingPage.css";

interface Slide {
  title: string;
  description: string;
  image: ImageName;
}

/** 피그마 「1. 공통 (온보딩·로그인)」 › 온보딩 (단계 = 1 / 2 / 3) */
const SLIDES: Slide[] = [
  {
    title: "가게의 가능성,\n청년의 아이디어로 가꾸다",
    description: "대학생과 사장님이\n함께 만드는 새로운 가능성",
    image: "onboarding1",
  },
  {
    title: "사장님의 의뢰와\n학생의 제안이 만나는 곳",
    description: "사장님의 의뢰와 학생의 제안이\n오가는 양방향 소통",
    image: "onboarding2",
  },
  {
    title: "서로를 가장 잘 아는 이웃이기에\n더 확실한 파트너",
    description: "일상 속에서 가게를 경험해 온\n대학생의 이해도와 안전한 협업 환경",
    image: "onboarding3",
  },
];

function OnboardingPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const slide = SLIDES[step];
  const isLast = step === SLIDES.length - 1;

  useEffect(() => {
    preloadImages(["onboarding2", "onboarding3"]);
  }, []);

  // 「시작하기」와 「SKIP」 모두 본 것으로 저장하고 로그인으로 간다. 뒤로 가기로 돌아오지 않게 교체한다.
  const finish = () => {
    markOnboardingSeen();
    navigate("/login", { replace: true });
  };

  const handleNext = () => {
    if (isLast) {
      finish();
      return;
    }
    setStep(step + 1);
  };

  return (
    <div className="onboarding">
      <AppBar
        right={
          <button type="button" className="onboarding__skip" onClick={finish}>
            SKIP &gt;&gt;
          </button>
        }
      />

      <main className="onboarding__content">
        <section key={step} className="onboarding__slide" aria-live="polite">
          <h2 className="onboarding__title">{slide.title}</h2>
          <div className="onboarding__illustration">
            <AppImage name={slide.image} priority={step === 0} />
          </div>
          <p className="onboarding__description">{slide.description}</p>
        </section>

        <div className="onboarding__dots">
          <PageDots total={SLIDES.length} current={step + 1} />
        </div>

        <Button fullWidth className="onboarding__next" onClick={handleNext}>
          {isLast ? "시작하기" : "다음으로"}
        </Button>
      </main>
    </div>
  );
}

export default OnboardingPage;
