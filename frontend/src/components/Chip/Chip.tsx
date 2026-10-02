import type { ButtonHTMLAttributes } from "react";
import AppImage from "../AppImage/AppImage";
import "./Chip.css";

interface ChipProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  label: string;
  selected?: boolean;
  /** filled = 업종 칩(회색 → 노랑), outlined = 역량 칩(흰색 테두리 칩 → 자주 바탕) */
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
      {selected && <AppImage name="iconCheckCircle18" />}
      {label}
    </button>
  );
}

export default Chip;
