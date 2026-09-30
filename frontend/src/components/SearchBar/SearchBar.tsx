import type { InputHTMLAttributes } from "react";
import MaskIcon from "../MaskIcon/MaskIcon";
import "./SearchBar.css";

interface SearchBarProps extends InputHTMLAttributes<HTMLInputElement> {
  /** 스크롤해도 화면 위에 붙는 상단 바 (탐색 탭) */
  sticky?: boolean;
}

/** 검색창. 기본 = 회색 검색칸, sticky = 반투명 흰 띠 위에 고정 */
function SearchBar({
  sticky = false,
  placeholder = "가게 이름이나 작업으로 검색",
  className = "",
  ...rest
}: SearchBarProps) {
  return (
    <div className={`search-bar${sticky ? " search-bar--sticky" : ""} ${className}`.trim()}>
      <label className="search-bar__field">
        <MaskIcon name="iconTabSearch" size={18} />
        <input
          type="search"
          className="search-bar__input"
          placeholder={placeholder}
          aria-label="검색"
          {...rest}
        />
      </label>
    </div>
  );
}

export default SearchBar;
