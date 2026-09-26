import type { ButtonHTMLAttributes } from "react";
import AppImage from "../AppImage/AppImage";
import "./KakaoLoginButton.css";

function KakaoLoginButton({
  type = "button",
  className = "",
  ...rest
}: Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children">) {
  return (
    <button type={type} className={`kakao-login-button ${className}`.trim()} {...rest}>
      <AppImage name="logoKakao" className="kakao-login-button__symbol" priority />
      카카오 로그인
    </button>
  );
}

export default KakaoLoginButton;
