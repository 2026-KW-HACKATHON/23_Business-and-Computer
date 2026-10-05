import { useState } from "react";
import "./ReferencePhotos.css";

interface ReferencePhotosProps {
  /** 사진 주소 (제안 referenceImageUrls 등) */
  urls: string[];
}

/**
 * 참고 사진 썸네일 (한 줄 3칸 정사각형). 누르면 새 탭에서 원본을 연다.
 * 불러오지 못한 사진은 회색 칸으로 남긴다. 파일 이름만 있는 칸은 AttachmentTiles 를 쓴다.
 */
function ReferencePhotos({ urls }: ReferencePhotosProps) {
  const [failed, setFailed] = useState<ReadonlySet<string>>(new Set());

  return (
    <ul className="reference-photos">
      {urls.map((url, i) => (
        <li key={`${i}-${url}`} className="reference-photos__tile">
          {failed.has(url) ? (
            <span className="reference-photos__broken" role="img" aria-label={`참고 사진 ${i + 1} (불러오지 못함)`} />
          ) : (
            <a href={url} target="_blank" rel="noreferrer" className="reference-photos__link">
              <img
                className="reference-photos__image"
                src={url}
                alt={`참고 사진 ${i + 1}`}
                loading="lazy"
                onError={() => setFailed((prev) => new Set(prev).add(url))}
              />
            </a>
          )}
        </li>
      ))}
    </ul>
  );
}

export default ReferencePhotos;
