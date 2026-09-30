import type { ButtonHTMLAttributes } from "react";
import AppImage from "../AppImage/AppImage";
import "./TextButton.css";

interface TextButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** 오른쪽 › 표시 */
  showChevron?: boolean;
  /** 왼쪽 필터 아이콘 (예: 목록 「최신순」 정렬) */
  showFilter?: boolean;
}

function TextButton({
  showChevron = true,
  showFilter = false,
  type = "button",
  className = "",
  children,
  ...rest
}: TextButtonProps) {
  return (
    <button type={type} className={`text-button ${className}`.trim()} {...rest}>
      {showFilter && <AppImage name="iconFilter14" />}
      <span>{children}</span>
      {showChevron && <AppImage name="iconChevronRight14" />}
    </button>
  );
}

export default TextButton;
