import type { ButtonHTMLAttributes } from "react";
import "./DownloadButton.css";

/** 파일 받기 버튼 (회색 알약). 결과물 보기의 원본 파일 목록에 쓴다 */
function DownloadButton({
  type = "button",
  className = "",
  children = "받기",
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button type={type} className={`download-button ${className}`.trim()} {...rest}>
      <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
        <path
          d="M6 2v5.5M3.5 5 6 7.5 8.5 5M2 10.2h8"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      {children}
    </button>
  );
}

export default DownloadButton;
