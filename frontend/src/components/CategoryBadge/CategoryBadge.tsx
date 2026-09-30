import type { Field } from "../../types/field";
import "./CategoryBadge.css";

interface CategoryBadgeProps {
  field: Field;
}

/** 제안·의뢰 대분류 태그. 카드 제목 아래에 쓴다 */
function CategoryBadge({ field }: CategoryBadgeProps) {
  return <span className="category-badge">{field}</span>;
}

export default CategoryBadge;
