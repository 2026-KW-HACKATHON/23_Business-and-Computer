import type { ButtonHTMLAttributes } from "react";
import type { Role } from "../../types/role";
import "./Button.css";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** primary = 역할 색 버튼, secondary = 회색 보조 버튼 */
  variant?: "primary" | "secondary";
  /** 사장님 = 노랑, 학생 = 자주 */
  tone?: Role;
  /** large = 52px, medium = 48px (카드 안), small = 40px (목록 줄 안) */
  size?: "large" | "medium" | "small";
  fullWidth?: boolean;
}

function Button({
  variant = "primary",
  tone = "owner",
  size = "large",
  fullWidth = false,
  type = "button",
  className = "",
  ...rest
}: ButtonProps) {
  const classes = [
    "button",
    variant === "primary" ? `button--${tone}` : "button--secondary",
    size === "large" ? "" : `button--${size}`,
    fullWidth ? "button--full" : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return <button type={type} className={classes} {...rest} />;
}

export default Button;
