# 0077. Deadline as a dot and short date, card titles that wrap less

## Status

Accepted. The deadline badge on request cards is now a dot, the stage, and a
short date (「● 초안 ~10/14」) instead of an outlined pill with
「초안 10월 14일까지」. Card heads keep the icon and the deadline on the first
title line, and titles wrap only when they must. On the student home, 새로
올라온 가게 고민 rows end with the text link 「제안하기 ›」 instead of a pill.

## Context

- The badge (`DeadlineBadge`) was a white pill with a black border, a gray
  stage, and a bold date with a highlighter, e.g. 「초안 10월 14일까지」. It
  took about 120px, so titles such as 「점심 특선 전단지 급하게」 broke into two
  short lines, and its black border was darker than the title.
- Card titles used `text-wrap: balance`, which evens out the two lines and
  makes short first lines. The head centered the icon and badge vertically,
  so with a two-line title they sat between the lines.
- The 「제안하기」 pill in 새로 올라온 가게 고민 looked like a tag rather than
  an action.

## Decision

- **Deadline** (`src/components/DeadlineBadge/DeadlineBadge.tsx`): no chip.
  A 6px dot (owner yellow before the draft, danger red after it), the stage
  in gray (12px), and the date as 「~M/D」 in bold black (13px). The badge is
  22px tall, the same as one title line. `formatDueShort` in
  `src/lib/date.ts` makes 「~10/14」; the student request card and the owner
  explore card use it.
- **Card heads** (`ExploreCard`, student `RequestCard`): `align-items:
  flex-start`, so the kind icon and the deadline line up with the first
  title line; titles use `text-wrap: pretty` with `word-break: keep-all`.
- **새로 올라온 가게 고민**: 「제안하기 ›」 as plain text in the student color
  (13px bold), no background; pressing the row dims it.

## Rationale

- The short date and the missing border give the title most of the width
  back, so most titles fit on one line.
- The dot color still separates draft (yellow) from final (red) at a glance,
  which the old 1px vs 1.5px border did not.
- A text link with 「›」 reads as an action and matches other 「… ›」 links.

## Alternatives Considered

- **Same pill with the short date**: shorter, but the black border still
  outweighed the title.
- **Borderless yellow chip** (light for draft, strong for final): clear, but
  heavier than needed next to the category tags.
- **D-day (「D-3」)**: the owner home chose dates over D-day on 9/29.

## Agent Guidance

- Use `formatDueShort` for any new deadline badge; do not pass 「…까지」
  strings.
- Figma: page 0 「뱃지 / 마감」 holds the new look (the old one is archived);
  pages 2 and 3 card heads are top-aligned with one-line titles.
