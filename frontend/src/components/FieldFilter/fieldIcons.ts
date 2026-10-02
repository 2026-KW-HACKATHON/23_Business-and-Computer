import type { Field } from "../../types/field";
import type { ImageName } from "../AppImage/images";

/** 분야마다 3D 업종 아이콘 (피그마 「필터 / 분야」 설명의 대응표) */
export const FIELD_ICONS: Record<Field, ImageName> = {
  디자인: "icon3dCraft",
  홍보: "icon3dPhoto",
  "개발·IT": "icon3dElectronics",
  분석: "icon3dBank",
  "글쓰기·번역": "icon3dBooks",
  기타: "icon3dHardware",
};
