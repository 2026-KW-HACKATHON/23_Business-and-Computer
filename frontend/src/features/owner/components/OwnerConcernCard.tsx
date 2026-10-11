import { useState } from "react";
import { AppImage, TextButton } from "../../../components";
import { formatMonthDay } from "../../../lib/date";
import type { OwnerConcern } from "../lib/storeConcern";
import "./OwnerConcernCard.css";

interface OwnerConcernCardProps {
  /** 해결되지 않은 고민. 없으면 null */
  concern: OwnerConcern | null;
  /** 고민 올리기(없을 때) · 고치기(있을 때) 화면으로 */
  onOpen: () => void;
}

/**
 * 사장님 홈 「확인할 일」 아래 가게 고민 (ADR 0070).
 * 고민이 없으면 흰 바탕 · 노랑 점선의 「빈 자리」 카드라 위 「맡길 일 찾기」 배너와 겹쳐 보이지 않는다.
 * 고민이 있으면 확인할 일 카드(흰 바탕 · 노랑 테두리 · 꽉 찬 버튼)와 달리 사장님이 털어놓는 말풍선이다.
 * 왼쪽 아래 생각하는 사장님 그림, 꼬리 달린 흰 말풍선 안에 노랑 따옴표 · 제목 · 「— 우리 가게 고민 · 분야 · 올린 날」,
 * 말풍선 아래 오른쪽에 글자 버튼 「고치기 ›」. 섹션 제목 「현재 올려둔 고민」은 홈이 붙인다.
 * 제목 옆 「펼치기」를 누르면 말풍선 안에 회색 「자세한 설명」 상자가 열린다 (설명이 없으면 버튼 없음).
 */
function OwnerConcernCard({ concern, onOpen }: OwnerConcernCardProps) {
  const [open, setOpen] = useState(false);

  if (!concern) {
    return (
      <button type="button" className="owner-concern-card owner-concern-card--empty" onClick={onOpen}>
        <AppImage name="concernOwner" width={44} alt="" />
        <span className="owner-concern-card__text">
          <span className="owner-concern-card__empty-title">우리 가게 고민을 남겨 보세요</span>
          <span className="owner-concern-card__empty-description">학생들이 보고 해결 방법을 먼저 제안해 줘요</span>
          <span className="owner-concern-card__link">고민 올리기 ›</span>
        </span>
      </button>
    );
  }
  const sign = ["우리 가게 고민", concern.categoryName, formatMonthDay(concern.createdAt.slice(0, 10))]
    .filter(Boolean)
    .join(" · ");
  return (
    <div className="owner-concern-card owner-concern-card--posted">
      <div className="owner-concern-card__speaker">
        <AppImage name="concernOwner" width={56} alt="" className="owner-concern-card__illustration" />
        <div className="owner-concern-card__bubble">
          <span className="owner-concern-card__quote" aria-hidden="true">
            “
          </span>
          <div className="owner-concern-card__title-row">
            <h3 className="owner-concern-card__title">{concern.title}</h3>
            {concern.description && (
              <TextButton
                className="owner-concern-card__toggle"
                showChevron={!open}
                aria-expanded={open}
                onClick={() => setOpen((v) => !v)}
              >
                {open ? "접기" : "펼치기"}
              </TextButton>
            )}
          </div>
          <p className="owner-concern-card__sign">— {sign}</p>
          {open && concern.description && (
            <div className="owner-concern-card__status">
              <span className="owner-concern-card__status-label">자세한 설명</span>
              <p className="owner-concern-card__status-detail">{concern.description}</p>
            </div>
          )}
        </div>
      </div>
      <TextButton className="owner-concern-card__edit" onClick={onOpen}>
        고치기
      </TextButton>
    </div>
  );
}

export default OwnerConcernCard;
