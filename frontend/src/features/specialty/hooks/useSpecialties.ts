import { useCallback, useEffect, useState } from "react";
import { fetchSpecialties } from "../api/specialtyApi";
import type { SpecialtyLoad } from "../types";

/**
 * 특기 목록을 불러온다. 실패하면 status 가 error 이고, reload 로 다시 불러온다.
 * 화면을 떠난 뒤 온 응답은 버린다.
 */
export function useSpecialties(): { load: SpecialtyLoad; reload: () => void } {
  const [load, setLoad] = useState<SpecialtyLoad>({ status: "loading" });
  const [request, setRequest] = useState(0);

  useEffect(() => {
    let active = true;
    fetchSpecialties().then(
      (categories) => {
        if (active) setLoad({ status: "loaded", categories });
      },
      () => {
        if (active) setLoad({ status: "error" });
      },
    );
    return () => {
      active = false;
    };
  }, [request]);

  const reload = useCallback(() => {
    setLoad({ status: "loading" });
    setRequest((n) => n + 1);
  }, []);

  return { load, reload };
}
