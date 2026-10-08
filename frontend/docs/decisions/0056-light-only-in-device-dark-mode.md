# 0056. The app stays light when the device uses dark mode

## Status

Accepted. Fields, buttons, and text keep their light colors when the phone
or computer is set to dark mode.

## Context

- The design has light screens only; Figma has no dark version.
- `src/index.css` still held the Vite starter's `color-scheme: light dark`
  and a `prefers-color-scheme: dark` block. In dark mode the browser drew
  fields and buttons with its dark defaults. On 가게 정보 수정, 가게 소개
  turned near black (rgb(59, 59, 59)) under the black text, a contrast of
  1.75. Buttons without their own text color turned their text white, and
  the shared text color turned light gray.
- Comparing ten owner screens in light and dark emulation showed 220
  elements whose colors changed.

## Decision

- `:root` sets `color-scheme: only light`, and `index.html` has
  `<meta name="color-scheme" content="only light">`.
- `src/index.css` has one set of root text and background colors, the light
  ones, for every mode.
- `only` also opts the page out of the browsers' automatic darkening of
  light pages (Chrome on Android, in-app web views).

## Rationale

- One rule at the root fixes every field, button, and text color at once,
  including screens added later.
- Declaring light only is how a page tells the browser not to darken it; the
  per-element colors stay as Figma draws them.

## Alternatives Considered

- Setting a background and text color on each field: misses fields added
  later and does not stop automatic darkening.
- A dark theme: there is no dark design to follow.

## Agent Guidance

- Do not add `prefers-color-scheme` rules or system colors (`Canvas`,
  `ButtonText`); give new fields an explicit background and text color from
  `src/styles/tokens.css`.
- To check, emulate dark mode and compare a screen's computed colors with
  light mode; they should be the same.
