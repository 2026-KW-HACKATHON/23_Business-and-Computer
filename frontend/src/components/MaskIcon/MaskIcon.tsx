import { IMAGES } from "../AppImage/images";
import type { ImageName } from "../AppImage/images";
import "./MaskIcon.css";

interface MaskIconProps {
  name: ImageName;
  /** 한 변 크기(px). 없으면 이미지 원래 크기 */
  size?: number;
  className?: string;
}

/** 한 가지 색 아이콘을 글자색(currentColor)으로 칠한다. 선택·비선택처럼 색이 바뀌는 곳에 쓴다 */
function MaskIcon({ name, size, className = "" }: MaskIconProps) {
  const image = IMAGES[name];
  // 작은 SVG는 작은따옴표가 든 data URL로 들어오므로 큰따옴표로 감싼다
  const url = `url("${image.src}")`;

  return (
    <span
      className={`mask-icon ${className}`.trim()}
      aria-hidden="true"
      style={{
        width: size ?? image.width,
        height: size ?? image.height,
        WebkitMaskImage: url,
        maskImage: url,
      }}
    />
  );
}

export default MaskIcon;
