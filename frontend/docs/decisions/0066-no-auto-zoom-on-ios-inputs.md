# 0066. iPhone and iPad do not zoom in when an input is tapped

## Status

Accepted. On iOS the viewport gets `maximum-scale=1`, so tapping an input no
longer zooms the screen in. Other devices keep the plain viewport.

## Context

- The user asked whether sending a chat message zooms the phone browser in.
  It does on iPhone: Safari (and every iOS browser, all WebKit) zooms in when
  an input whose text is smaller than 16px gets focus, and stays zoomed after
  the keyboard closes.
- The app's inputs follow the Figma sizes: the chat input (owner · student),
  the search bar, and `TextField` are 14px; the long text fields (the request
  description, the store introduction) are 15px. Only the budget field is
  16px.
- Android Chrome does not zoom in on focus. There, `maximum-scale=1` would
  also block pinch zoom.

## Decision

- A small inline script in `index.html`, right after the viewport meta, adds
  「, maximum-scale=1」 to it when the device is iOS: the user agent has
  iPhone, iPad, or iPod, or it looks like a Mac with touch points (iPadOS).
  It runs before the app, so the first tap already behaves.
- The input font sizes stay as designed.

## Rationale

- iOS ignores `maximum-scale` for pinch zoom (since iOS 10), so users can
  still zoom by hand; only the automatic focus zoom stops.
- Keeping Android's viewport untouched keeps pinch zoom there.
- Raising every input to 16px would change the Figma sizes across the app.

## Alternatives Considered

- 16px text in every input (or only on iOS through CSS): larger text than
  the design and many rules to override.
- `maximum-scale=1` (or `user-scalable=no`) for every device: blocks pinch
  zoom on Android.

## Agent Guidance

- New inputs may use the design sizes; the iOS viewport rule covers them.
- Keep the script next to the viewport meta in `index.html`.
