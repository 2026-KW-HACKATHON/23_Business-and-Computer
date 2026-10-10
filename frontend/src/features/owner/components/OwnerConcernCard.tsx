import { useState } from "react";
import { AppImage, Button, CategoryBadge, TextButton } from "../../../components";
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
 * 고민이 있으면 확인할 일 카드와 같은 틀(제목 · 분야 · 올린 날 · 「고치기」)이고, 섹션 제목
 * 「현재 올려둔 고민」은 홈이 붙인다. 제목 옆 「펼치기」를 누르면 회색 「자세한 설명」 상자가 열린다
 * (설명이 없으면 버튼 없음).
 */
function OwnerConcernCard({ concern, onOpen }: OwnerConcernCardProps) {
  const [open, setOpen] = useState(false);

  if (!concern) {
    return (
      <button type="button" className="owner-concern-card owner-concern-card--empty" onClick={onOpen}>
        <AppImage name="sorryOwner" width={48} alt="" />
        <span className="owner-concern-card__text">
          <span className="owner-concern-card__empty-title">우리 가게 고민을 남겨 보세요</span>
          <span className="owner-concern-card__empty-description">학생들이 보고 해결 방법을 먼저 제안해 줘요</span>
          <span className="owner-concern-card__link">고민 올리기 ›</span>
        </span>
      </button>
    );
  }
  return (
    <div className="owner-concern-card">
      <div className="owner-concern-card__heading">
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
        <div className="owner-concern-card__meta">
          {concern.categoryName && <CategoryBadge field={concern.categoryName} />}
          <span className="owner-concern-card__meta-text">
            {formatMonthDay(concern.createdAt.slice(0, 10))}에 올렸어요
          </span>
        </div>
      </div>
      {open && concern.description && (
        <div className="owner-concern-card__status">
          <span className="owner-concern-card__status-label">자세한 설명</span>
          <p className="owner-concern-card__status-detail">{concern.description}</p>
        </div>
      )}
      <Button variant="secondary" fullWidth onClick={onOpen}>
        고치기
      </Button>
    </div>
  );
}

export default OwnerConcernCard;
