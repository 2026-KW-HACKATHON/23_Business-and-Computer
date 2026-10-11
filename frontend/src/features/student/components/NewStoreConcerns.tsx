import { useNavigate } from "react-router-dom";
import { RoleAvatar, SectionHeader } from "../../../components";
import { formatMonthDay } from "../../../lib/date";
import { STUDENT_PATHS } from "../lib/paths";
import type { ExploreStore } from "../types";
import "./NewStoreConcerns.css";

/** 홈에 보이는 고민 수 */
const SHOWN = 3;

interface NewStoreConcernsProps {
  /** 학생 홈(GET /me/home)의 newStoreConcerns: 고민이 있는 가게, 최근에 올리거나 고친 순 (ADR 0080) */
  stores: ExploreStore[];
}

/**
 * 학생 홈 「새로 올라온 가게 고민」 (ADR 0071). 고민이 있는 가게를
 * 최근에 올리거나 고친 순으로 3곳까지 흰 카드 하나에 한 줄씩 보인다 (가게 사진 · 「가게 · 업종 · 날짜」 · 고민 한 줄,
 * 오른쪽 「제안하기」). 줄을 누르면 탐색 「가게」의 「제안하기」처럼 그 가게를 고른 채 제안 보내기 2/4 로 간다.
 * 「전체 ›」는 탐색 「가게」 탭.
 * 고민은 제안의 참고 정보라 제안 내용을 채우지 않는다 (ADR 0069).
 * 고민 있는 가게가 없거나 홈에서 이 섹션만 못 불러왔으면(빈 목록) 섹션째 숨겨 나머지 홈은 그대로다.
 */
function NewStoreConcerns({ stores: concernStores }: NewStoreConcernsProps) {
  const navigate = useNavigate();
  const stores = concernStores.filter((store) => store.concern).slice(0, SHOWN);
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
              <span className="new-concerns__action">제안하기 ›</span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default NewStoreConcerns;
