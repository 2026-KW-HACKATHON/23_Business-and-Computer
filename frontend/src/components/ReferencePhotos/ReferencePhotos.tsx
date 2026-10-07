import { useState } from "react";
import "./ReferencePhotos.css";

interface ReferencePhotosProps {
  /** 사진 주소. 서버 사진(https, 제안 referenceImageUrls 등)이나 보내기 전에 고른 사진의 미리보기(blob:) */
  urls: string[];
}

/** 열어 볼 수 있는 사진 주소. 그 밖의 주소는 링크로 걸지 않는다 */
function isPhotoUrl(url: string): boolean {
  return url.startsWith("https://") || url.startsWith("blob:");
}

/**
 * 참고 사진 썸네일 (한 줄 3칸 정사각형). 누르면 새 탭에서 원본을 크게 연다.
 * 불러오지 못한 사진은 회색 칸으로 남긴다. 파일 이름만 있는 칸은 AttachmentTiles 를 쓴다.
 */
function ReferencePhotos({ urls }: ReferencePhotosProps) {
  const [failed, setFailed] = useState<ReadonlySet<string>>(new Set());

  return (
    <ul className="reference-photos">
      {urls.map((url, i) => (
        <li key={`${i}-${url}`} className="reference-photos__tile">
          {failed.has(url) || !isPhotoUrl(url) ? (
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
