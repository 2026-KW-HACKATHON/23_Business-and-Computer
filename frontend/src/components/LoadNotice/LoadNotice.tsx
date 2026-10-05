import "./LoadNotice.css";

interface LoadNoticeProps {
  status: "loading" | "error";
  /** 예: 가게 목록을 불러오는 중이에요 */
  loadingText: string;
  /** 예: 가게 목록을 불러오지 못했어요 */
  errorText: string;
  onRetry: () => void;
}

/** 목록을 불러오는 중이거나 실패했을 때 한 줄 안내. 실패면 「다시 시도」 버튼을 붙인다 */
function LoadNotice({ status, loadingText, errorText, onRetry }: LoadNoticeProps) {
  if (status === "loading") {
    return (
      <p className="load-notice" role="status">
        {loadingText}
      </p>
    );
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
