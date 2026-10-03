import { useNavigate } from "react-router-dom";

/**
 * 앱바 「←」. 앱 안에서 들어왔으면 바로 앞 화면으로,
 * 주소로 바로 들어와 앞 화면이 없으면 fallback 으로 간다.
 */
export function useBack(fallback: string) {
  const navigate = useNavigate();

  return () => {
    const index = (window.history.state as { idx?: number } | null)?.idx ?? 0;
    if (index > 0) navigate(-1);
    else navigate(fallback, { replace: true });
  };
}
