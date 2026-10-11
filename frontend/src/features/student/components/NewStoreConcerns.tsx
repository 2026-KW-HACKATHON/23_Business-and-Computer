import { useNavigate } from "react-router-dom";
import { RoleAvatar, SectionHeader } from "../../../components";
import { formatMonthDay } from "../../../lib/date";
import { useExploreStores } from "../hooks/useExploreStores";
import { STUDENT_PATHS } from "../lib/paths";
import { sortStoresByConcern } from "../lib/storeConcern";
import "./NewStoreConcerns.css";

/** 홈에 보이는 고민 수 */
const SHOWN = 3;

/**
 * 학생 홈 「새로 올라온 가게 고민」 (ADR 0071). GET /explore/stores 에서 고민이 있는 가게를
 * 최근에 올리거나 고친 순으로 3곳까지 흰 카드 하나에 한 줄씩 보인다 (가게 사진 · 「가게 · 업종 · 날짜」 · 고민 한 줄,
 * 오른쪽 「제안하기」). 줄을 누르면 탐색 「가게」의 「제안하기」처럼 그 가게를 고른 채 제안 보내기 2/4 로 간다.
 * 「전체 ›」는 탐색 「가게」 탭.
 * 고민은 제안의 참고 정보라 제안 내용을 채우지 않는다 (ADR 0069).
 * 불러오는 중 · 실패 · 고민 있는 가게가 없으면 섹션째 숨겨 나머지 홈은 그대로다.
 */
function NewStoreConcerns() {
  const navigate = useNavigate();
  const { load } = useExploreStores();
  if (load.status !== "loaded") return null;
  const stores = sortStoresByConcern(load.stores)
    .filter((store) => store.concern)
    .slice(0, SHOWN);
  if (stores.length === 0) return null;

  return (
    <section className="student-home__section">
      <SectionHeader
        title="새로 올라온 가게 고민"
        actionLabel="전체 ›"
        onAction={() => navigate(STUDENT_PATHS.exploreStores)}
      />
      <ul className="new-concerns">
        {stores.map((store) => (
          <li key={store.ownerProfileId}>
            <button
              type="button"
              className="new-concerns__row"
              onClick={() => navigate(STUDENT_PATHS.newProposalStep(2), { state: { store } })}
            >
              <RoleAvatar role="owner" size={32} src={store.photo} />
              <span className="new-concerns__text">
                <span className="new-concerns__meta">
                  {[store.name, store.category, store.concern && formatMonthDay(store.concern.updatedAt.slice(0, 10))]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
                <strong className="new-concerns__title">“{store.concern?.title}”</strong>
              </span>
              <span className="new-concerns__action">제안하기</span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default NewStoreConcerns;
