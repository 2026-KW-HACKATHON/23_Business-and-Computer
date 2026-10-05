import "./TrustChips.css";

interface TrustChipsProps {
  proposalCount: number;
  noShowCount: number;
}

/** 학생 신뢰 표시: 광운대 인증 · 제안 N회 · 패널티 N회 (노쇼 · 마감 초과로 받은 페널티) */
function TrustChips({ proposalCount, noShowCount }: TrustChipsProps) {
  return (
    <ul className="trust-chips">
      <li className="trust-chips__chip trust-chips__chip--verified">
        <span className="trust-chips__check" aria-hidden="true">
          <svg viewBox="0 0 10 10" fill="none">
            <path
              d="M2 5.2 4.1 7.2 8 3"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
        광운대 인증
      </li>
      <li className="trust-chips__chip">제안 {proposalCount}회</li>
      <li className="trust-chips__chip">패널티 {noShowCount}회</li>
    </ul>
  );
}

export default TrustChips;
