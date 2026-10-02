import { SubScreen } from "../../../components";
import "./OwnerMissing.css";

interface OwnerMissingProps {
  title: string;
  onBack: () => void;
  message?: string;
}

/** 주소의 id 에 맞는 내용이 없을 때 */
function OwnerMissing({ title, onBack, message = "찾는 내용이 없어요" }: OwnerMissingProps) {
  return (
    <SubScreen title={title} onBack={onBack}>
      <p className="owner-missing">{message}</p>
    </SubScreen>
  );
}

export default OwnerMissing;
