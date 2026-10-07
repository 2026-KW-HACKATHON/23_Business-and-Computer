import { useEffect, useState } from "react";
import type { MouseEvent } from "react";
import { createPortal } from "react-dom";
import "./ReferencePhotos.css";

interface ReferencePhotosProps {
  /** 사진 주소. 서버 사진(https, 제안 referenceImageUrls 등)이나 보내기 전에 고른 사진의 미리보기(blob:) */
  urls: string[];
  /** urls 와 같은 순서의 파일 이름 (보내기 전에 고른 사진). 없으면 주소 끝 이름, 그것도 알아볼 수 없으면 「사진 n」 */
  names?: string[];
}

/** 열어 볼 수 있는 사진 주소. 그 밖의 주소는 회색 칸으로 둔다 */
function isPhotoUrl(url: string): boolean {
  return url.startsWith("https://") || url.startsWith("blob:");
}

/** 배지에 보일 이름. 서버가 붙인 무작위 이름(uuid 같은 긴 영숫자)은 알아볼 수 없어 「사진 n」으로 */
function photoName(url: string, index: number, name: string | undefined): string {
  if (name) return name;
  if (url.startsWith("https://")) {
    try {
      const last = decodeURIComponent(new URL(url).pathname.split("/").pop() ?? "");
      if (last && !/^[0-9a-f-]{16,}\.[a-z0-9]+$/i.test(last)) return last;
    } catch {
      // 이름을 읽지 못하면 「사진 n」
    }
  }
  return `사진 ${index + 1}`;
}

/**
 * 참고 사진 썸네일 (한 줄 3칸 정사각형). 칸마다 파일 이름 배지가 붙고, 누르면 앱 안에서 크게 본다.
 * 불러오지 못한 사진은 회색 칸으로 남긴다. 사진 없이 파일 이름만 있는 칸은 AttachmentTiles 를 쓴다.
 */
function ReferencePhotos({ urls, names }: ReferencePhotosProps) {
  const [failed, setFailed] = useState<ReadonlySet<string>>(new Set());
  const [open, setOpen] = useState<number | null>(null);
  const labels = urls.map((url, i) => photoName(url, i, names?.[i]));
  const isBroken = (url: string) => failed.has(url) || !isPhotoUrl(url);
  // 크게 볼 수 있는 사진의 순서 (불러오지 못한 사진은 건너뛴다)
  const viewable = urls.flatMap((url, i) => (isBroken(url) ? [] : [i]));

  return (
    <>
      <ul className="reference-photos">
        {urls.map((url, i) => (
          <li key={`${i}-${url}`} className="reference-photos__tile">
            {isBroken(url) ? (
              <span className="reference-photos__broken" role="img" aria-label={`${labels[i]} (불러오지 못함)`} />
            ) : (
              <button
                type="button"
                className="reference-photos__open"
                aria-label={`${labels[i]} 크게 보기`}
                onClick={() => setOpen(i)}
              >
                <img
                  className="reference-photos__image"
                  src={url}
                  alt=""
                  loading="lazy"
                  onError={() => setFailed((prev) => new Set(prev).add(url))}
                />
              </button>
            )}
            <span className="reference-photos__name" aria-hidden="true">
              {labels[i]}
            </span>
          </li>
        ))}
      </ul>
      {open !== null && viewable.includes(open) && (
        <PhotoViewer
          urls={urls}
          labels={labels}
          viewable={viewable}
          index={open}
          onIndex={setOpen}
          onClose={() => setOpen(null)}
        />
      )}
    </>
  );
}

interface PhotoViewerProps {
  urls: string[];
  labels: string[];
  /** 넘겨 볼 수 있는 사진의 순서 */
  viewable: number[];
  index: number;
  onIndex: (index: number) => void;
  onClose: () => void;
}

/** 사진 크게 보기. 화면을 덮고, 바깥 · 사진을 누르거나 Esc 로 닫는다. 여러 장이면 양옆 화살표 · 방향키로 넘긴다 */
function PhotoViewer({ urls, labels, viewable, index, onIndex, onClose }: PhotoViewerProps) {
  const position = viewable.indexOf(index);
  const many = viewable.length > 1;

  useEffect(() => {
    const step = (delta: number) =>
      onIndex(viewable[(position + delta + viewable.length) % viewable.length]);
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      else if (e.key === "ArrowRight" && viewable.length > 1) step(1);
      else if (e.key === "ArrowLeft" && viewable.length > 1) step(-1);
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [position, viewable, onIndex, onClose]);

  // 보는 동안 뒤 화면이 스크롤되지 않게
  useEffect(() => {
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prevOverflow;
    };
  }, []);

  const go = (delta: number) => (e: MouseEvent) => {
    e.stopPropagation();
    onIndex(viewable[(position + delta + viewable.length) % viewable.length]);
  };

  return createPortal(
    <div
      className="photo-viewer"
      role="dialog"
      aria-modal="true"
      aria-label={`${labels[index]} 크게 보기`}
      onClick={onClose}
    >
      <div className="photo-viewer__top">
        <span className="photo-viewer__name">{labels[index]}</span>
        {many && (
          <span className="photo-viewer__count">
            {position + 1} / {viewable.length}
          </span>
        )}
        <button type="button" className="photo-viewer__close" aria-label="닫기" onClick={onClose}>
          ✕
        </button>
      </div>
      <img className="photo-viewer__image" src={urls[index]} alt={labels[index]} />
      {many && (
        <>
          <button type="button" className="photo-viewer__nav photo-viewer__nav--prev" aria-label="이전 사진" onClick={go(-1)}>
            ‹
          </button>
          <button type="button" className="photo-viewer__nav photo-viewer__nav--next" aria-label="다음 사진" onClick={go(1)}>
            ›
          </button>
        </>
      )}
    </div>,
    document.body,
  );
}

export default ReferencePhotos;
