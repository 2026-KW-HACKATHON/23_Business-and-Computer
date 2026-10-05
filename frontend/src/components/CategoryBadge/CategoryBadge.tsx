import "./CategoryBadge.css";

interface CategoryBadgeProps {
  /** 피그마 분야(Field) 또는 서버 대분류 이름 (GET /specialties · 제안 specialtyCategories) */
  field: string;
}

/** 제안·의뢰 대분류 태그. 카드 제목 아래에 쓴다 */
function CategoryBadge({ field }: CategoryBadgeProps) {
  return <span className="category-badge">{field}</span>;
}

export default CategoryBadge;
