import { Outlet } from "react-router-dom";
import { StudentSignupProvider } from "../features/signup";

/** /signup/student/* 의 부모 라우트. 1/3 ~ 완료 화면이 입력값을 나눠 쓴다 */
function StudentSignupLayout() {
  return (
    <StudentSignupProvider>
      <Outlet />
    </StudentSignupProvider>
  );
}

export default StudentSignupLayout;
