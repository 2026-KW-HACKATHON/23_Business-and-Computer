import type { ButtonHTMLAttributes } from "react";
import AppImage from "../AppImage/AppImage";
import "./TextButton.css";

interface TextButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** 오른쪽 › 표시 */
  showChevron?: boolean;
}

function TextButton({
  showChevron = true,
  type = "button",
  className = "",
  children,
  ...rest
}: TextButtonProps) {
  return (
    <button type={type} className={`text-button ${className}`.trim()} {...rest}>
      <span>{children}</span>
      {showChevron && <AppImage name="iconChevronRight14" />}
    </button>
  );
}

export default TextButton;
