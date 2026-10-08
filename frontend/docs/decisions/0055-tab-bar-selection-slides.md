# 0055. The tab bar's selection slides to the new tab

## Status

Accepted. Tapping another tab in the bottom tab bar slides the gray selection
pill from the old tab to the new one and pops the new tab's icon once.

## Context

- The user asked for feedback inside the tab bar when a tap changes the tab,
  in the manner of the 당근 app. No public write-up of 당근's tab motion was
  found, so the motion follows this bar's own shape: a glass bar with a gray
  pill behind the current tab.
- Each main tab is its own route, and every tab screen mounts its own
  `MainTabScreen`, so a tab change draws a new `TabBar` that does not know
  where the pill was.
- Tab changes have no screen transition. Pressing a tab shrinks it to 94% with
  the shared press shade (ADR 0046).
- Figma: 「하단 탭바 (글래스 · 3칸)」 (node 2107-289) on 「0. 스타일 가이드」;
  its description states the motion.

## Decision

- **Selection pill** (`.tab-bar__indicator` in `TabBar`): one element a tab
  wide behind the tabs, placed by `--tab-index`. The current tab draws no
  fill of its own.
- **Slide**: `TabBar` keeps the last drawn tab in a module variable. A new
  bar for a different tab puts the pill at the old tab, makes the browser
  compute that place (`getBoundingClientRect`), and sets the new tab before
  the first paint, so the pill slides over 0.38s
  (`cubic-bezier(0.34, 1.18, 0.64, 1)`, a slight overshoot). While moving it
  stretches to 116% wide and 92% high and back (`tab-bar-stretch`).
- **New icon**: the new tab's icon grows to 120% and back once, starting 0.12s
  after the slide (`tab-bar-pop`, 0.4s).
- The first bar after the app opens and a bar for the same tab do not move.
- `prefers-reduced-motion` places the pill and the icon without motion.

## Rationale

- The pill already marks the current tab; moving it shows where the tap went
  without new shapes and keeps the feedback inside the bar.
- Moving the pill before the first paint does not wait for animation frames,
  which stop while the window is hidden.

## Alternatives Considered

- Moving the pill one `requestAnimationFrame` later: frames do not run in a
  hidden window, so the pill can stay at the old tab.
- A screen transition between tabs: tabs are peers, and a sliding screen
  reads as going deeper.
- A Lottie animation per icon: needs new assets for every icon.

## Agent Guidance

- The slide relies on the module variable, not on the bar staying mounted;
  keep one `TabBar` per tab screen.
- The pill is a third of the bar wide. A fourth tab changes the
  `.tab-bar__indicator` width along with `TABS`.
