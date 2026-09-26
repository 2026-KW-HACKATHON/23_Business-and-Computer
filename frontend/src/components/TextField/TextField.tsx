import { useId } from "react";
import type { InputHTMLAttributes } from "react";
import "./TextField.css";

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
  /** 칸 바로 아래 빨간 안내 문구 */
  errorText?: string;
}

function TextField({ invalid = false, errorText, className = "", id, ...rest }: TextFieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const errorId = `${inputId}-error`;
  const showError = invalid && errorText;

  return (
    <div className={`text-field ${className}`.trim()}>
      <input
        id={inputId}
        className={`text-field__input${invalid ? " text-field__input--invalid" : ""}`}
        aria-invalid={invalid || undefined}
        aria-describedby={showError ? errorId : undefined}
        {...rest}
      />
      {showError && (
        <p id={errorId} className="text-field__error">
          {errorText}
        </p>
      )}
    </div>
  );
}

export default TextField;
