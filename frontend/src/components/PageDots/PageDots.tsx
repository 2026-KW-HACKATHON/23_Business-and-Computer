import "./PageDots.css";

interface PageDotsProps {
  total: number;
  /** 1부터 시작 */
  current: number;
}

function PageDots({ total, current }: PageDotsProps) {
  return (
    <div className="page-dots" aria-label={`${total}쪽 중 ${current}쪽`}>
      {Array.from({ length: total }, (_, i) => (
        <span
          key={i}
          className={`page-dots__dot${i + 1 === current ? " page-dots__dot--active" : ""}`}
        />
      ))}
    </div>
  );
}

export default PageDots;
