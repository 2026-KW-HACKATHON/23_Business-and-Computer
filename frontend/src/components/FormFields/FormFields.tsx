import type { ReactNode } from "react";
import TextField from "../TextField/TextField";
import { formatMonthDayWeekday, todayIsoDate } from "../../lib/date";
import "./FormFields.css";

/** 초안 마감 · 최종 마감 ("2026-09-27", 아직 안 골랐으면 "") */
export interface DueDates {
  draftDue: string;
  finalDue: string;
}

const REVISIONS_MIN = 1;
const REVISIONS_MAX = 5;

interface FormFieldProps {
  label: string;
  /** 이름 옆 작은 회색 설명 */
  hint?: string;
  /** 입력칸 하나를 감쌀 때 true. 이름을 눌러도 그 칸으로 간다 */
  wrapsInput?: boolean;
  children: ReactNode;
}

/** 입력 묶음 하나: 굵은 이름 (+ 회색 설명) 과 입력칸 */
export function FormField({ label, hint, wrapsInput = false, children }: FormFieldProps) {
  const head = (
    <span className="request-field__label">
      {label}
      {hint && <small>{hint}</small>}
    </span>
  );
  return wrapsInput ? (
    <label className="request-field">
      {head}
      {children}
    </label>
  ) : (
    <div className="request-field">
      {head}
      {children}
    </div>
  );
}

interface TitleFieldProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

export function TitleField({ value, onChange, placeholder }: TitleFieldProps) {
  return (
    <TextField
      className="request-field__input"
      value={value}
      maxLength={40}
      placeholder={placeholder}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

interface TextAreaFieldProps {
  value: string;
  onChange: (value: string) => void;
  maxLength: number;
  placeholder?: string;
}

/** 여러 줄 입력칸 + 오른쪽 아래 글자 수 */
export function TextAreaField({ value, onChange, maxLength, placeholder }: TextAreaFieldProps) {
  return (
    <span className="request-field__textarea-box">
      <textarea
        className="request-field__textarea"
        value={value}
        maxLength={maxLength}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
      />
      <span className="request-field__count">
        {value.length}/{maxLength}
      </span>
    </span>
  );
}

interface BudgetFieldProps {
  /** 원. 안 적었으면 0 */
  value: number;
  onChange: (value: number) => void;
}

/** 작업비 입력칸. 쉼표를 넣어 보여 주고 숫자만 받는다 */
export function BudgetField({ value, onChange }: BudgetFieldProps) {
  return (
    <TextField
      className="request-field__input request-field__budget"
      inputMode="numeric"
      placeholder="0"
      value={value > 0 ? value.toLocaleString("ko-KR") : ""}
      onChange={(e) => onChange(Number(e.target.value.replace(/\D/g, "").slice(0, 8)))}
      trailing="원"
    />
  );
}

interface DueBoxProps {
  label: string;
  value: string;
  min: string;
  max?: string;
  onChange: (value: string) => void;
}

/** 마감일 칸. 칸 전체가 날짜 입력이고 고른 날은 「9월 27일 (일)」로 보인다 */
function DueBox({ label, value, min, max, onChange }: DueBoxProps) {
  return (
    <label className="request-field__due">
      <span className="request-field__due-label">{label}</span>
      <span
        className={`request-field__due-value${value ? "" : " request-field__due-value--empty"}`}
      >
        {value ? formatMonthDayWeekday(value) : "날짜 고르기"}
      </span>
      <input
        type="date"
        className="request-field__due-input"
        aria-label={label}
        value={value}
        min={min}
        max={max}
        onChange={(e) => onChange(e.target.value)}
        onClick={(e) => {
          try {
            e.currentTarget.showPicker();
          } catch {
            // 달력을 바로 열 수 없는 브라우저는 기본 동작을 따른다
          }
        }}
      />
    </label>
  );
}

interface DueDateFieldsProps {
  value: DueDates;
  onChange: (value: DueDates) => void;
  /** 두 칸 이름. 기본 「초안 마감 · 최종 마감」 (지원하기는 「초안 보내는 날 · 최종본 드리는 날」) */
  labels?: [string, string];
  /** 이 날보다 늦게 고를 수 없다 (지원하기: 사장님이 정한 마감) */
  max?: DueDates;
}

/** 초안 마감 · 최종 마감 두 칸. 지난 날은 고를 수 없다 */
export function DueDateFields({
  value,
  onChange,
  labels = ["초안 마감", "최종 마감"],
  max,
}: DueDateFieldsProps) {
  const today = todayIsoDate();
  return (
    <div className="request-field__dues">
      <DueBox
        label={labels[0]}
        value={value.draftDue}
        min={today}
        max={max?.draftDue}
        // 초안 마감을 최종 마감보다 뒤로 옮기면 최종 마감은 다시 고른다
        onChange={(draftDue) =>
          onChange({
            draftDue,
            finalDue: value.finalDue && value.finalDue < draftDue ? "" : value.finalDue,
          })
        }
      />
      <DueBox
        label={labels[1]}
        value={value.finalDue}
        min={value.draftDue || today}
        max={max?.finalDue}
        onChange={(finalDue) => onChange({ draftDue: value.draftDue, finalDue })}
      />
    </div>
  );
}

interface RevisionStepperProps {
  value: number;
  onChange: (value: number) => void;
}

/** 수정 횟수 − / + (최소 1회) */
export function RevisionStepper({ value, onChange }: RevisionStepperProps) {
  return (
    <div className="request-field__stepper">
      <p>초안을 받은 뒤 고쳐 달라고 할 수 있는 횟수</p>
      <button
        type="button"
        className="request-field__step-button"
        aria-label="수정 횟수 줄이기"
        disabled={value <= REVISIONS_MIN}
        onClick={() => onChange(value - 1)}
      >
        −
      </button>
      <span className="request-field__revisions" aria-live="polite">
        {value}회
      </span>
      <button
        type="button"
        className="request-field__step-button"
        aria-label="수정 횟수 늘리기"
        disabled={value >= REVISIONS_MAX}
        onClick={() => onChange(value + 1)}
      >
        +
      </button>
    </div>
  );
}
