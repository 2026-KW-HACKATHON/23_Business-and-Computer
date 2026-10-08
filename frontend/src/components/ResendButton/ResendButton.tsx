import "./ResendButton.css";

interface ResendButtonProps {
  /** 한 번 누르면 true. 초록 「재발송되었어요.」로 바뀐다 */
  sent: boolean;
  onClick: () => void;
  /** 보내는 중이거나 재발송 제한 시간 동안 */
  disabled?: boolean;
}

/** 학생 인증의 「인증번호 재발송」 글자 버튼 */
function ResendButton({ sent, onClick, disabled = false }: ResendButtonProps) {
  return (
    <button
      type="button"
      className={`resend-button press-text${sent ? " resend-button--sent" : ""}`}
      onClick={onClick}
      disabled={disabled}
      aria-live="polite"
    >
      {sent ? "재발송되었어요." : "인증번호 재발송"}
    </button>
  );
}

export default ResendButton;
