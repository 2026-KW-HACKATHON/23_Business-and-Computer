import { AppImage, BottomSheet } from "../../../components";
import type { ExploreSort } from "../api/exploreApi";
import { SORT_LABEL } from "../lib/explore";
import "./ExploreSortSheet.css";

interface ExploreSortSheetProps {
  open: boolean;
  value: ExploreSort;
  /** 공감 많은 순을 보일지 (제안 탭에서만) */
  likes: boolean;
  onSelect: (sort: ExploreSort) => void;
  onClose: () => void;
}

/** 탐색 「최신순」 버튼을 누르면 뜨는 정렬 고르기 */
function ExploreSortSheet({ open, value, likes, onSelect, onClose }: ExploreSortSheetProps) {
  const options: ExploreSort[] = likes ? ["LATEST", "OLDEST", "LIKES"] : ["LATEST", "OLDEST"];

  return (
    <BottomSheet open={open} onClose={onClose} title="정렬">
      <ul className="explore-sort">
        {options.map((sort) => (
          <li key={sort}>
            <button
              type="button"
              className={`explore-sort__option${sort === value ? " explore-sort__option--selected" : ""}`}
              aria-pressed={sort === value}
              onClick={() => onSelect(sort)}
            >
              {SORT_LABEL[sort]}
              {sort === value && <AppImage name="iconCheckSuccess14" alt="" />}
            </button>
          </li>
        ))}
      </ul>
    </BottomSheet>
  );
}

export default ExploreSortSheet;
