import type { ButtonHTMLAttributes } from "react";
import "./DemoButton.css";

/** 로그인 화면의 「로그인 없이 둘러보기(Demo)」 버튼. 받침 없는 평평한 회색 버튼 */
function DemoButton({
  type = "button",
  className = "",
  children = "로그인 없이 둘러보기(Demo)",
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type={type} className={`demo-button ${className}`.trim()} {...rest}>
      {children}
    </button>
  );
}

export default DemoButton;
