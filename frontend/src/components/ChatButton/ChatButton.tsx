import type { Role } from "../../types/role";
import MaskIcon from "../MaskIcon/MaskIcon";
import "./ChatButton.css";

interface ChatButtonProps {
  /** 사장님 = 노랑, 학생 = 자주 */
  role: Role;
  onClick: () => void;
  /** 화면 읽기용 이름 (예: 김광운 학생과 채팅) */
  label?: string;
}

/** 채팅방으로 가는 40px 네모 버튼. 주요 버튼처럼 받침이 있다 */
function ChatButton({ role, onClick, label = "채팅" }: ChatButtonProps) {
  return (
    <button
      type="button"
      className={`chat-button chat-button--${role}`}
      aria-label={label}
      onClick={onClick}
    >
      <MaskIcon name="iconTabChat" size={24} />
    </button>
  );
}

export default ChatButton;
