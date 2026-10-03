import "./InfoRows.css";

interface InfoRowsProps {
  rows: { label: string; value: string }[];
  /** large = 받은 제안의 희망 작업비처럼 조금 큰 글자 */
  size?: "default" | "large";
}

/** 「예산 50,000원」처럼 왼쪽 이름 + 오른쪽 값 줄 목록 */
function InfoRows({ rows, size = "default" }: InfoRowsProps) {
  return (
    <dl className={`info-rows info-rows--${size}`}>
      {rows.map(({ label, value }) => (
        <div key={label} className="info-rows__row">
          <dt className="info-rows__label">{label}</dt>
          <dd className="info-rows__value">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export default InfoRows;
