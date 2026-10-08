import Loader from "../Loader/Loader";
import LoadingDots from "../LoadingDots/LoadingDots";
import SkeletonList from "../SkeletonList/SkeletonList";
import { useDelayedShow } from "../../hooks/useDelayedShow";
import "./LoadNotice.css";

interface LoadNoticeProps {
  status: "loading" | "error";
  /** 예: 가게 목록을 불러오는 중이에요 */
  loadingText: string;
  /** 예: 가게 목록을 불러오지 못했어요 */
  errorText: string;
  onRetry: () => void;
  /**
   * 불러오는 동안의 모양. 기본 rows.
   * page = 화면 가운데 캐릭터 로딩 (화면 하나를 통째로 기다릴 때),
   * rows · cards = 목록 · 카드 모양의 회색 틀, block = 칸 하나의 회색 틀,
   * more = 목록 끝의 작은 점 (끝까지 내려 더 불러올 때)
   */
  layout?: "page" | "rows" | "cards" | "block" | "more";
}

/** 불러오는 중이면 자리에 맞는 로딩, 실패면 한 줄 안내와 「다시 시도」 */
function LoadNotice({ status, loadingText, errorText, onRetry, layout = "rows" }: LoadNoticeProps) {
  const dotsShown = useDelayedShow();
  if (status === "loading") {
    if (layout === "page") return <Loader label={loadingText} />;
    if (layout === "more") {
      return <p className="load-notice">{dotsShown && <LoadingDots label={loadingText} />}</p>;
    }
    return <SkeletonList kind={layout} label={loadingText} />;
  }
  return (
    <div className="load-notice" role="alert">
      <span>{errorText}</span>
      <button type="button" className="load-notice__retry" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  );
}

export default LoadNotice;
