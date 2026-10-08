import { useEffect, useState } from "react";
import ownerMotion from "../../assets/loading/owner-action-loading.svg";
import ownerStill from "../../assets/loading/owner-action-loading-static.svg";
import studentMotion from "../../assets/loading/student-action-loading.svg";
import studentStill from "../../assets/loading/student-action-loading-static.svg";
import { LOADING_DELAY_MS } from "../../hooks/useDelayedShow";
import type { Role } from "../../types/role";
import "./Loader.css";

const ART: Record<Role, { motion: string; still: string }> = {
  owner: { motion: ownerMotion, still: ownerStill },
  student: { motion: studentMotion, still: studentStill },
};

/** 화면 주소의 역할 (<html data-role>, RoleColorScope). 역할이 없는 화면은 사장님 캐릭터 */
function screenRole(): Role {
  return document.documentElement.dataset.role === "student" ? "student" : "owner";
}

/** 캐릭터가 점 위를 뛰는 그림만 (120×87). 동작 줄이기면 멈춘 그림 */
export function LoaderArt({ tone }: { tone: Role }) {
  const art = ART[tone];
  return (
    <picture className="loader-art">
      <source media="(prefers-reduced-motion: reduce)" srcSet={art.still} />
      <img src={art.motion} width={120} height={87} alt="" />
    </picture>
  );
}

interface LoaderProps {
  /** 캐릭터 아래 문구 (예: 의뢰서를 불러오는 중이에요) */
  label: string;
  /** 캐릭터. 정하지 않으면 화면 주소의 역할 */
  tone?: Role;
  /** 화면 전체를 흰 막으로 덮는다 (로그인 처리처럼 앱 전체가 기다릴 때) */
  overlay?: boolean;
}

/**
 * 화면 하나를 통째로 기다릴 때 가운데에 두는 캐릭터 로딩 (골목인턴 로딩 v7).
 * 0.3초 안에 끝나면 아무것도 그리지 않는다
 */
function Loader({ label, tone, overlay = false }: LoaderProps) {
  const [role, setRole] = useState<Role | null>(null);
  useEffect(() => {
    const timer = window.setTimeout(() => setRole(tone ?? screenRole()), LOADING_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [tone]);

  return (
    <div className={`loader${overlay ? " loader--overlay" : ""}`} role="status" aria-live="polite">
      {role && (
        <>
          <LoaderArt tone={role} />
          <p className="loader__label">{label}</p>
        </>
      )}
    </div>
  );
}

export default Loader;
