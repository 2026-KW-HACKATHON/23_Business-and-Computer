import "./LabelChip.css";

interface LabelChipProps {
  label: string;
}

/** 누를 수 없는 테두리 칩 (예: 할 일, 학생 역량). 고르는 칩은 Chip 을 쓴다 */
function LabelChip({ label }: LabelChipProps) {
  return <span className="label-chip">{label}</span>;
}

export default LabelChip;
