import { useId } from "react";
import type { InputHTMLAttributes, ReactNode } from "react";
import "./TextField.css";

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
  /** 칸 바로 아래 빨간 안내 문구 */
  errorText?: string;
  /** 칸 안 오른쪽 끝 요소 (예: 이메일 인증의 「변경」 버튼) */
  trailing?: ReactNode;
}

function TextField({
  invalid = false,
  errorText,
  trailing,
  className = "",
  id,
  readOnly,
  ...rest
}: TextFieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const errorId = `${inputId}-error`;
  const showError = invalid && errorText;
  const boxClasses = [
    "text-field__box",
    invalid ? "text-field__box--invalid" : "",
    readOnly ? "text-field__box--locked" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={`text-field ${className}`.trim()}>
      <div className={boxClasses}>
        <input
          id={inputId}
          className="text-field__input"
          readOnly={readOnly}
          aria-invalid={invalid || undefined}
          aria-describedby={showError ? errorId : undefined}
          {...rest}
        />
        {trailing && <span className="text-field__trailing">{trailing}</span>}
      </div>
      {showError && (
        <p id={errorId} className="text-field__error">
          {errorText}
        </p>
      )}
    </div>
  );
}

export default TextField;
