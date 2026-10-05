import { DemoStrip } from "../../../components";
import { useIsDemo } from "../hooks/useDemoSession";

/** 둘러보기(데모 로그인) 중에만 모든 화면 위에 「둘러보기 중」 띠를 그린다 */
function DemoSessionStrip() {
  return useIsDemo() ? <DemoStrip /> : null;
}

export default DemoSessionStrip;
