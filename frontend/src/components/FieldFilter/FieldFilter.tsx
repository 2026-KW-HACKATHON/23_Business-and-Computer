import type { Field } from "../../types/field";
import AppImage from "../AppImage/AppImage";
import type { ImageName } from "../AppImage/images";
import "./FieldFilter.css";

/** 분야마다 3D 업종 아이콘 (피그마 「필터 / 분야」 설명의 대응표) */
const FIELD_ICONS: Record<Field, ImageName> = {
  디자인: "icon3dCraft",
  홍보: "icon3dPhoto",
  "개발·IT": "icon3dElectronics",
  분석: "icon3dBank",
  "글쓰기·번역": "icon3dBooks",
  기타: "icon3dHardware",
};

interface FieldFilterProps {
  /** 없으면 「모든 분야」 */
  field?: Field;
  selected: boolean;
  onClick: () => void;
}

/** 탐색의 분야 필터 한 칸 (아이콘 + 아래 이름) */
function FieldFilter({ field, selected, onClick }: FieldFilterProps) {
  return (
    <button
      type="button"
      className={`field-filter${selected ? " field-filter--selected" : ""}`}
      aria-pressed={selected}
      onClick={onClick}
    >
      <span className="field-filter__icon">
        <AppImage name={field ? FIELD_ICONS[field] : "iconFieldAll"} alt="" />
      </span>
      {field ?? "모든 분야"}
    </button>
  );
}

export default FieldFilter;
