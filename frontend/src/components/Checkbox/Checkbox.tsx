import "./Checkbox.css";

interface CheckboxProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  /** 문구 아래 작은 회색 설명 (예: 노쇼 페널티 안내) */
  description?: string;
}

/** 약관·약속 동의 체크. 역할 상관없이 검정 바탕 + 연노랑 체크 한 가지 */
function Checkbox({ checked, onChange, label, description }: CheckboxProps) {
  return (
    <label className="checkbox">
      <input
        type="checkbox"
        className="checkbox__input"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span className="checkbox__box" aria-hidden="true">
        <svg width="14" height="10.4" viewBox="-1 -1 14 10.4" fill="none">
          <path
            d="M0 4.2L4.2 8.4L12 0"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      <span className="checkbox__text">
        <span className="checkbox__label">{label}</span>
        {description && <span className="checkbox__description">{description}</span>}
      </span>
    </label>
  );
}

export default Checkbox;
