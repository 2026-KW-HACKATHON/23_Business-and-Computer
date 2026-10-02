import { Outlet } from "react-router-dom";
import { OwnerSignupProvider } from "../features/signup";

/** /signup/owner/* 의 부모 라우트. 1/3 ~ 완료 화면이 입력값을 나눠 쓴다 */
function OwnerSignupLayout() {
  return (
    <OwnerSignupProvider>
      <Outlet />
    </OwnerSignupProvider>
  );
}

export default OwnerSignupLayout;
