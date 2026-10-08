import type { ButtonHTMLAttributes } from "react";
import type { Role } from "../../types/role";
import LoadingDots from "../LoadingDots/LoadingDots";
import "./Button.css";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** primary = 역할 색 버튼, secondary = 회색 보조 버튼 */
  variant?: "primary" | "secondary";
  /** 사장님 = 노랑, 학생 = 자주 */
  tone?: Role;
  /** large = 52px, medium = 48px (카드 안), small = 40px (목록 줄 안) */
  size?: "large" | "medium" | "small";
  fullWidth?: boolean;
  /** 보내는 중: 글자 자리에 점 세 개가 움직이고 너비는 그대로. 누를 수 없다 */
  loading?: boolean;
  /** 보내는 중일 때 화면 낭독기가 읽는 글 (예: 보내는 중) */
  loadingLabel?: string;
}

function Button({
  variant = "primary",
  tone = "owner",
  size = "large",
  fullWidth = false,
  type = "button",
  className = "",
  loading = false,
  loadingLabel = "처리하는 중",
  disabled,
  children,
  ...rest
}: ButtonProps) {
  const classes = [
    "button",
    variant === "primary" ? `button--${tone}` : "button--secondary",
    size === "large" ? "" : `button--${size}`,
    fullWidth ? "button--full" : "",
    loading ? "button--loading" : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? (
        <>
          <span className="button__label">{children}</span>
          <LoadingDots tone="current" className="button__dots" label={loadingLabel} />
        </>
      ) : (
        children
      )}
    </button>
  );
}

export default Button;
