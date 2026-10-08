import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useProposalExamples } from "./useStudentData";
import { loadStudentHome } from "../lib/studentHome";
import type { ProposalExample, StudentHome } from "../types";

/**
 * 학생 홈을 불러온 상태. retrying 은 홈을 보이는 채로 못 불러온 섹션을 다시 부르는 중
 * (그 섹션 자리에 불러오는 중을 보인다)
 */
export type StudentHomeLoad =
  | { status: "loading" }
  | { status: "error" }
  | { status: "loaded"; home: StudentHome; retrying: boolean };

/**
 * 학생 홈 한 화면 분량 (GET /me/home 한 번, ADR 0065). 확인할 일 · 사장님이 확인 중 · 기다리는 중 ·
 * 다른 학생 제안 · 끝난 일을 서버가 모아 준다. 「이런 제안은 어때요?」 예시만 화면에 둔 예시다.
 * reload 는 같은 요청을 다시 보낸다: 홈을 보이는 중이면 그대로 두고(섹션 재시도), 아니면 처음부터.
 * 401 은 /login 으로 보낸다 (그동안은 loading). 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useStudentHome(): {
  load: StudentHomeLoad;
  reload: () => void;
  examples: ProposalExample[];
} {
  const navigate = useNavigate();
  const [load, setLoad] = useState<StudentHomeLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);
  const examples = useProposalExamples();

  useEffect(() => {
    let active = true;
    void loadStudentHome().then((result) => {
      if (!active) return;
      if (result.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (result.status === "loaded") {
        setLoad({ status: "loaded", home: result.home, retrying: false });
      } else {
        // 섹션 재시도가 통째로 실패했으면 보이던 홈을 두고 그 섹션의 「다시 시도」로 돌아간다
        setLoad((prev) => (prev.status === "loaded" ? { ...prev, retrying: false } : { status: "error" }));
      }
    });
    return () => {
      active = false;
    };
  }, [request, navigate]);

  const reload = useCallback(() => {
    setLoad((prev) => (prev.status === "loaded" ? { ...prev, retrying: true } : { status: "loading" }));
    setRequest((n) => n + 1);
  }, []);

  return { load, reload, examples };
}
