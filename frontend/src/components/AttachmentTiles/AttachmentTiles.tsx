import "./AttachmentTiles.css";

interface AttachmentTilesProps {
  /** 파일 이름. 사진 대신 회색 칸 아래에 이름을 띄운다 */
  names: string[];
  /** 칸 높이(px) */
  height?: number;
  /** names 와 같은 순서의 사진 주소 (고른 파일의 미리보기 등). 없으면 회색 칸 */
  srcs?: string[];
}

/** 참고 사진 · 참고 자료 칸. 사진 주소가 있으면 사진, 없으면 회색 칸 + 파일 이름 */
function AttachmentTiles({ names, height = 120, srcs }: AttachmentTilesProps) {
  return (
    <ul className="attachment-tiles">
      {names.map((name, i) => (
        <li key={`${i}-${name}`} className="attachment-tiles__tile" style={{ height }}>
          {srcs?.[i] && <img className="attachment-tiles__image" src={srcs[i]} alt="" />}
          <span className="attachment-tiles__name">{name}</span>
        </li>
      ))}
    </ul>
  );
}

export default AttachmentTiles;
