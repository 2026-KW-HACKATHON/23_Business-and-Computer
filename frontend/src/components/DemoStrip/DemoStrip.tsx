import "./DemoStrip.css";

/**
 * 둘러보기(데모 로그인) 중일 때 모든 화면 맨 위에 붙는 띠. 진입점(main.tsx)이 한 번만 그리고,
 * 화면 틀은 이 띠 높이(--demo-strip-height)만큼 짧아진다.
 */
function DemoStrip() {
  return (
    <div className="demo-strip" role="status">
      <span className="demo-strip__dot" aria-hidden="true" />
      둘러보기 중 · 체험용 데모 계정이에요
    </div>
  );
}

export default DemoStrip;
