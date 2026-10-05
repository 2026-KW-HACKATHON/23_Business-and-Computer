import { SubScreen } from "../../../components";
import "./StudentMissing.css";

interface StudentMissingProps {
  title: string;
  onBack: () => void;
  message?: string;
}

/** 주소의 id 에 맞는 내용이 없을 때 */
function StudentMissing({ title, onBack, message = "찾는 내용이 없어요" }: StudentMissingProps) {
  return (
    <SubScreen title={title} onBack={onBack}>
      <p className="student-missing">{message}</p>
    </SubScreen>
  );
}

export default StudentMissing;
