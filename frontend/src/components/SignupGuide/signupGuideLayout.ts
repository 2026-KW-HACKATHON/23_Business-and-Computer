/** 덮개(앱 화면 폭) 기준 자리 */
export interface Box {
  top: number;
  left: number;
  width: number;
  height: number;
}

/** 첫 안내가 잰 뒤 화면 요소와 안내 글의 크기 */
export interface GuideMeasure {
  /** 덮개 크기 */
  width: number;
  height: number;
  card?: Box;
  bell?: Box;
  fab?: Box;
  /** 덮개의 ✕ · 「알겠어요」. 말풍선이 가리지 않게 피한다 */
  close?: Box;
  ok?: Box;
  /** 환영 문구 글자 크기 */
  welcome: { width: number; height: number };
  /** 말풍선 ①②③을 한 줄로 그렸을 때의 폭. 아직 재기 전이면 0 */
  tips: [number, number, number];
}

/** 말풍선 자리. width 가 있으면 그 폭으로 줄을 바꿔 그린다 */
export interface TipSpot {
  top: number;
  left: number;
  width?: number;
}

export interface GuideLayout {
  welcomeTop?: number;
  tip1?: TipSpot;
  tip2?: TipSpot;
  tip3?: TipSpot;
}

// 한 줄 말풍선 높이, 글줄 높이, 줄을 바꿨을 때 위아래 여백 (SignupGuide.css)
const TIP_HEIGHT = 42;
const TIP_LINE = 17;
const TIP_WRAP_PADDING = 16;
// 말풍선에서 글자 말고 차지하는 폭: 왼쪽 여백 10 · 번호 22 · 간격 8 · 오른쪽 여백 16
const TIP_CHROME = 56;
// 말풍선과 가리키는 것 사이
const GAP = 8;
// 화면 가장자리에서 띄우는 거리 (피그마의 ③은 4px 앞까지 온다)
const EDGE = 4;
// 환영 문구와 그 아래 것 사이 (피그마)
const WELCOME_GAP = 14;
// 다시 그린 종 동그라미의 반지름
const BELL_RADIUS = 22;
// 서로 이만큼은 떨어져 있어야 겹치지 않은 것으로 본다
const CLEARANCE = 4;

const overlaps = (a: Box, b: Box) =>
  a.left < b.left + b.width + CLEARANCE &&
  b.left < a.left + a.width + CLEARANCE &&
  a.top < b.top + b.height + CLEARANCE &&
  b.top < a.top + a.height + CLEARANCE;

/** 쓸 수 있는 폭 안에 한 줄로 들어가면 그대로, 아니면 그 폭으로 줄을 바꾼 크기 */
const tipSize = (natural: number, available: number) => {
  if (natural <= available) return { width: natural, height: TIP_HEIGHT, wrapped: false };
  const width = Math.max(available, TIP_CHROME + 40);
  const lines = Math.ceil((natural - TIP_CHROME) / (width - TIP_CHROME));
  return { width, height: Math.max(TIP_HEIGHT, lines * TIP_LINE + TIP_WRAP_PADDING), wrapped: true };
};

interface Candidate {
  box: Box;
  spot: TipSpot;
  welcomeTop?: number;
}

/**
 * 첫 안내의 환영 문구 · 말풍선 ①②③ 자리를 고른다 (ADR 0053). 화면이 짧거나 좁아도 서로 · 종 · 버튼 · ✕ ·
 * 「알겠어요」를 가리지 않고 화면 안에 들어가는 자리를 아래 후보에서 찾는다. 앞 후보일수록 피그마에 가깝고,
 * 다 안 되면 가장 덜 겹치는 자리. 쓸 수 있는 폭보다 긴 말풍선은 줄을 바꾼다.
 * - ① 확인할 일 카드 아래 → 아래 테두리에 걸치게 → 카드 안쪽 아래 → 위쪽 테두리에 걸치게 → 카드 안쪽 위 → 카드 위
 * - ② 새 의뢰 · 새 제안 버튼 왼쪽 → 버튼 위
 * - ③ 알림 종 왼쪽 → 종 아래
 * 카드 안을 가리는 것은 괜찮다 (안내용으로 다시 그린 그림)
 */
export function placeGuide(m: GuideMeasure): GuideLayout {
  const clampLeft = (left: number, width: number) => Math.max(EDGE, Math.min(left, m.width - EDGE - width));
  const full = m.width - EDGE * 2;
  const inView = (box: Box) => box.top >= CLEARANCE && box.top + box.height <= m.height - CLEARANCE;
  const welcomeBox = (top: number): Box => ({
    top,
    left: (m.width - m.welcome.width) / 2,
    width: m.welcome.width,
    height: m.welcome.height,
  });
  const candidate = (index: 0 | 1 | 2, available: number, place: (w: number, h: number) => { top: number; left: number }) => {
    const size = tipSize(m.tips[index], available);
    const { top, left } = place(size.width, size.height);
    const spot: TipSpot = { top, left: clampLeft(left, size.width), ...(size.wrapped ? { width: size.width } : {}) };
    return { spot, box: { top, left: spot.left, width: size.width, height: size.height } };
  };

  const obstacles: Box[] = [m.fab, m.close, m.ok].filter((box): box is Box => box !== undefined);
  const { card, fab, bell } = m;

  const tip3Options: Candidate[] = [];
  if (bell) {
    const cx = bell.left + bell.width / 2;
    const cy = bell.top + bell.height / 2;
    obstacles.push({ top: cy - BELL_RADIUS, left: cx - BELL_RADIUS, width: BELL_RADIUS * 2, height: BELL_RADIUS * 2 });
    tip3Options.push(
      candidate(2, cx - BELL_RADIUS - GAP - EDGE, (w, h) => ({ top: cy - h / 2, left: cx - BELL_RADIUS - GAP - w })),
      candidate(2, full, (w) => ({ top: cy + BELL_RADIUS + GAP, left: cx + BELL_RADIUS - w })),
    );
  }

  const tip2Options: Candidate[] = fab
    ? [
        candidate(1, fab.left - GAP - EDGE, (w, h) => ({ top: fab.top + fab.height / 2 - h / 2, left: fab.left - GAP - w })),
        candidate(1, full, (w, h) => ({ top: fab.top - GAP - h, left: fab.left + fab.width - w })),
      ]
    : [];

  const tip1Options: Candidate[] = [];
  if (card) {
    const bottom = card.top + card.height;
    const inside = card.width - 24;
    const welcomeAbove = (top: number, gap = WELCOME_GAP) => top - gap - m.welcome.height;
    const add = (available: number, place: (w: number, h: number) => { top: number; left: number }, welcomeTop?: (top: number) => number) => {
      const option = candidate(0, available, place);
      tip1Options.push({ ...option, welcomeTop: welcomeTop ? welcomeTop(option.box.top) : welcomeAbove(card.top) });
    };
    // 카드 아래 (피그마)
    add(full, () => ({ top: bottom + 12, left: card.left }));
    // 아래 테두리에 걸치게
    add(inside, (_w, h) => ({ top: bottom - h / 2, left: card.left + 12 }));
    // 카드 안쪽 아래 (흐리게 그린 카드 버튼 위)
    add(inside, (_w, h) => ({ top: bottom - 12 - h, left: card.left + 12 }));
    // 위쪽 테두리에 걸치게. 환영 문구는 그 위로 붙인다
    add(inside, (_w, h) => ({ top: card.top - h / 2, left: card.left + 12 }), (top) => welcomeAbove(top, GAP));
    // 카드 안쪽 위 (카드가 화면보다 긴 작은 폰)
    add(inside, () => ({ top: card.top + 12, left: card.left + 12 }));
    // 카드 위
    add(full, (_w, h) => ({ top: card.top - GAP - h, left: card.left }), (top) => welcomeAbove(top, GAP));
  }

  // 겹침 · 화면 밖은 크게, ②가 카드를 가리는 것은 조금 벌점. 같으면 앞 후보
  const pick = <T,>(options: T[]) => (options.length > 0 ? options : [undefined]);
  let best: { score: number; tip1?: Candidate; tip2?: Candidate; tip3?: Candidate } | undefined;
  pick(tip3Options).forEach((tip3, k) => {
    pick(tip1Options).forEach((tip1, i) => {
      pick(tip2Options).forEach((tip2, j) => {
        const placed: Box[] = [];
        if (tip3) placed.push(tip3.box);
        if (tip1) {
          placed.push(tip1.box);
          if (tip1.welcomeTop !== undefined) placed.push(welcomeBox(tip1.welcomeTop));
        }
        if (tip2) placed.push(tip2.box);
        let score = i + j * 0.5 + k * 0.8;
        placed.forEach((box, n) => {
          if (!inView(box)) score += 100;
          for (const other of [...obstacles, ...placed.slice(n + 1)]) {
            if (overlaps(box, other)) score += 50;
          }
        });
        if (tip2 && card && overlaps(tip2.box, card)) score += 3;
        if (!best || score < best.score) best = { score, tip1, tip2, tip3 };
      });
    });
  });

  return {
    welcomeTop: best?.tip1?.welcomeTop,
    tip1: best?.tip1?.spot,
    tip2: best?.tip2?.spot,
    tip3: best?.tip3?.spot,
  };
}
