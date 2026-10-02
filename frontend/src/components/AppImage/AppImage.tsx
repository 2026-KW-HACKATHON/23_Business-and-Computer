import type { ImgHTMLAttributes } from "react";
import { IMAGES } from "./images";
import type { ImageName } from "./images";

interface AppImageProps
  extends Omit<ImgHTMLAttributes<HTMLImageElement>, "src" | "width" | "height"> {
  name: ImageName;
  /** 표시 크기를 바꿀 때만 넣는다. 한쪽만 넣으면 비율대로 맞춘다. */
  width?: number;
  height?: number;
  /** 첫 화면에 바로 보이는 이미지면 true (지연 로딩 안 함) */
  priority?: boolean;
}

function AppImage({ name, width, height, priority = false, alt, ...rest }: AppImageProps) {
  const image = IMAGES[name];
  const ratio = image.height / image.width;
  const displayWidth = width ?? (height !== undefined ? height / ratio : image.width);
  const displayHeight = height ?? displayWidth * ratio;

  return (
    <img
      src={image.src}
      alt={alt ?? image.alt}
      width={displayWidth}
      height={displayHeight}
      loading={priority ? "eager" : "lazy"}
      decoding="async"
      fetchPriority={priority ? "high" : "auto"}
      draggable={false}
      {...rest}
    />
  );
}

export default AppImage;
