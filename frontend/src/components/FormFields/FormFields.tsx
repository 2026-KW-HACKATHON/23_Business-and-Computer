import { useState } from "react";
import type { ReactNode } from "react";
import BottomSheet from "../BottomSheet/BottomSheet";
import Button from "../Button/Button";
import DateWheel from "../DateWheel/DateWheel";
import TextField from "../TextField/TextField";
import { formatMonthDay, formatMonthDayWeekday, todayIsoDate } from "../../lib/date";
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
  /** 최대 글자 수. 기본은 의뢰 · 제안 제목의 40자 */
  maxLength?: number;
}

export function TitleField({ value, onChange, placeholder, maxLength = 40 }: TitleFieldProps) {
  return (
    <TextField
      className="request-field__input"
      value={value}
      maxLength={maxLength}
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

/** 마감일은 올해와 내년 안에서 고른다 */
function lastPickableDate(today: string): string {
  return `${Number(today.slice(0, 4)) + 1}-12-31`;
}

interface DueBoxProps {
  label: string;
  value: string;
  min: string;
  /** 하단 시트 제목 아래 설명 */
  description: string;
  onChange: (value: string) => void;
}

/**
 * 마감일 칸. 고른 날은 「9월 27일 (일)」로 보이고, 누르면 하단 시트에서 연 · 월 · 일 바퀴로 고른다
 * (피그마 「의뢰 등록 2/3 - 초안 · 최종 마감일 고르기 (팝업)」). 「이 날짜로 정하기」로 칸에 넣고,
 * 바깥을 누르거나 Esc 로 닫으면 바꾸지 않는다.
 */
function DueBox({ label, value, min, description, onChange }: DueBoxProps) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(value || min);
  const max = lastPickableDate(todayIsoDate());

  const openSheet = () => {
    setDraft(value && value >= min ? value : min);
    setOpen(true);
  };

  return (
    <>
      <button type="button" className="request-field__due" onClick={openSheet}>
        <span className="request-field__due-label">{label}</span>
        <span
          className={`request-field__due-value${value ? "" : " request-field__due-value--empty"}`}
        >
          {value ? formatMonthDayWeekday(value) : "날짜 고르기"}
        </span>
      </button>
      <BottomSheet
        open={open}
        onClose={() => setOpen(false)}
        title={`${label}일`}
        description={description}
        footer={
          <Button
            fullWidth
            onClick={() => {
              onChange(draft);
              setOpen(false);
            }}
          >
            이 날짜로 정하기
          </Button>
        }
      >
        <DateWheel value={draft} min={min} max={max} onChange={setDraft} />
      </BottomSheet>
    </>
  );
}

interface DueDateFieldsProps {
  value: DueDates;
  onChange: (value: DueDates) => void;
}

/** 초안 마감 · 최종 마감 두 칸. 지난 날은 고를 수 없다 */
export function DueDateFields({
  value,
  onChange,
}: DueDateFieldsProps) {
  const today = todayIsoDate();
  return (
    <div className="request-field__dues">
      <DueBox
        label="초안 마감"
        value={value.draftDue}
        min={today}
        description="학생이 초안을 보내는 날이에요"
        // 초안 마감을 최종 마감보다 뒤로 옮기면 최종 마감은 다시 고른다
        onChange={(draftDue) =>
          onChange({
            draftDue,
            finalDue: value.finalDue && value.finalDue < draftDue ? "" : value.finalDue,
          })
        }
      />
      <DueBox
        label="최종 마감"
        value={value.finalDue}
        min={value.draftDue || today}
        description={
          value.draftDue
            ? `초안 마감(${formatMonthDay(value.draftDue)})과 같거나 뒤로 골라 주세요`
            : "초안 마감과 같거나 뒤로 골라 주세요"
        }
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
