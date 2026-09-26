import type { ButtonHTMLAttributes } from "react";
import AppImage from "../AppImage/AppImage";
import "./Chip.css";

interface ChipProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  label: string;
  selected?: boolean;
  /** filled = 업종 칩(회색·노랑), outlined = 역량 칩(흰색·검정 테두리) */
  variant?: "filled" | "outlined";
}

function Chip({
  label,
  selected = false,
  variant = "filled",
  type = "button",
  className = "",
  ...rest
}: ChipProps) {
  const classes = ["chip", `chip--${variant}`, selected ? "chip--selected" : "", className]
    .filter(Boolean)
    .join(" ");

  return (
    <button type={type} className={classes} aria-pressed={selected} {...rest}>
      {selected && variant === "filled" && <AppImage name="iconCheckCircle18" />}
      {selected && variant === "outlined" && (
        <span className="chip__check">
          <AppImage name="iconCheck11" />
        </span>
      )}
      {label}
    </button>
  );
}

export default Chip;
