import { memo } from "react";
import type { ReactNode } from "react";

interface FrozenProps {
  /** false 면 마지막으로 그린 모습을 그대로 둔다 */
  live: boolean;
  children: ReactNode;
}

/** 닫히는 애니메이션 동안 부모가 내용을 먼저 지워도 마지막 모습을 그대로 보인다 */
const Frozen = memo(
  function Frozen({ children }: FrozenProps) {
    return <>{children}</>;
  },
  (_prev, next) => !next.live,
);

export default Frozen;
