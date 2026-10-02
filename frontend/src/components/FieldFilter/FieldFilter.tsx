import type { Field } from "../../types/field";
import AppImage from "../AppImage/AppImage";
import { FIELD_ICONS } from "./fieldIcons";
import "./FieldFilter.css";

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
