import AppImage from "../AppImage/AppImage";
import "./MenuList.css";

export interface MenuItem {
  label: string;
  /** 없으면 누를 수 없는 줄 */
  onClick?: () => void;
  /** 빨간 글자, › 없음 (예: 로그아웃) */
  danger?: boolean;
}

interface MenuListProps {
  items: MenuItem[];
}

/** 테두리 상자 안 메뉴 목록. 줄마다 이름 + 오른쪽 › */
function MenuList({ items }: MenuListProps) {
  return (
    <div className="menu-list">
      {items.map(({ label, onClick, danger }) => {
        const className = `menu-list__item${danger ? " menu-list__item--danger" : ""}`;
        const content = (
          <>
            <span>{label}</span>
            {!danger && <AppImage name="iconChevronRight14" width={16} alt="" />}
          </>
        );
        return onClick ? (
          <button key={label} type="button" className={className} onClick={onClick}>
            {content}
          </button>
        ) : (
          <div key={label} className={className}>
            {content}
          </div>
        );
      })}
    </div>
  );
}

export default MenuList;
