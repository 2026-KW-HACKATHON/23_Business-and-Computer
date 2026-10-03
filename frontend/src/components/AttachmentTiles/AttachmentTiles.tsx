import "./AttachmentTiles.css";

interface AttachmentTilesProps {
  /** 파일 이름. 사진 대신 회색 칸 아래에 이름을 띄운다 */
  names: string[];
  /** 칸 높이(px) */
  height?: number;
}

/** 참고 사진 · 참고 자료 칸. 사진을 연동하기 전까지 회색 칸 + 파일 이름 */
function AttachmentTiles({ names, height = 120 }: AttachmentTilesProps) {
  return (
    <ul className="attachment-tiles">
      {names.map((name) => (
        <li key={name} className="attachment-tiles__tile" style={{ height }}>
          <span className="attachment-tiles__name">{name}</span>
        </li>
      ))}
    </ul>
  );
}

export default AttachmentTiles;
