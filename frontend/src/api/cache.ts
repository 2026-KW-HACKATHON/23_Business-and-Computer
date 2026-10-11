/**
 * Memory cache for the list GETs that the 홈 · 탐색 · 내 활동 tabs call again
 * every time a tab mounts (ADR 0077). It is stale-while-revalidate: a tab that
 * opens again shows the remembered list at once, the list is asked for again in
 * the background, and screens that use {@link subscribeApiCache} redraw when
 * the new answer differs. So a change by the other side (e.g. a new 지원자)
 * shows up one round trip after the tab opens, and while a screen stays open
 * it shows up when a new notification arrives ({@link refreshApiCache}).
 *
 * Staleness rules — a cached answer must never hide the user's own change:
 * - Any non-GET backend call (POST · PUT · PATCH · DELETE, success or failure)
 *   clears the whole cache before it is sent and again when it settles.
 * - A GET that was in flight while a write happened is not stored, so an answer
 *   read before the write cannot land in the cache after it.
 * - Entries belong to the access token (scope), so 둘러보기 role switches and
 *   other accounts never share answers. Other tabs of the same browser are told
 *   to clear too (BroadcastChannel).
 * - Only the paths in {@link CACHEABLE} are cached. Chat, notifications, job
 *   details and payments always go to the network.
 */

/** An answer older than this is not shown; the screen waits for the network instead */
export const API_CACHE_MAX_AGE_MS = 5 * 60_000;
/** An answer younger than this is shown without asking again (screens mounting together, redraws) */
const REVALIDATE_AFTER_MS = 2_000;

/** Tab lists that are fetched again on every tab switch */
const CACHEABLE: readonly RegExp[] = [
  /^\/me\/home$/,
  /^\/explore(\?|$)/,
  /^\/explore\/stores(\?|$)/,
  /^\/me\/jobs\?status=[A-Z]+$/,
  /^\/me\/job-applications$/,
  /^\/me\/proposals$/,
  /^\/me\/received-proposals$/,
  /^\/owners\/me$/,
  /^\/owners\/me\/concern$/,
  /^\/students\/me$/,
  /^\/specialties$/,
  /^\/business-categories$/,
];

interface Entry {
  scope: string;
  at: number;
  value: unknown;
  /** Same request again, for background revalidation */
  load: () => Promise<unknown>;
}

const entries = new Map<string, Entry>();
/** GETs on the way, so the same list asked twice at once (e.g. two screens mounting) goes out once */
const inFlight = new Map<string, Promise<unknown>>();
/** Bumped by every clear; a GET stores its answer only if no clear happened while it was in flight */
let generation = 0;
/** Bumped when a background answer differs from the shown one; screens redraw on it */
let revision = 0;
const listeners = new Set<() => void>();

const channel: BroadcastChannel | null =
  typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel("gakkum-api-cache");
channel?.addEventListener("message", () => clearLocal());
// In Node (scripts that import the API modules) an open channel would keep the process alive
(channel as { unref?: () => void } | null)?.unref?.();

function clearLocal(): void {
  entries.clear();
  inFlight.clear();
  generation += 1;
}

/** Drops every cached answer here and in the other tabs of this browser. */
export function clearApiCache(): void {
  clearLocal();
  channel?.postMessage("clear");
}

export function isCacheable(method: string, path: string): boolean {
  return method === "GET" && CACHEABLE.some((pattern) => pattern.test(path));
}

/** Number that changes when a cached list got a newer answer (for useSyncExternalStore). */
export function getApiCacheRevision(): number {
  return revision;
}

export function subscribeApiCache(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * Sends `load()` once per key at a time and stores its answer unless the cache
 * was cleared meanwhile. Resolves to the answer; `changed` tells whether it
 * differs from the entry that was shown before.
 */
function fetchInto<T>(key: string, scope: string, load: () => Promise<T>): Promise<{ value: T; changed: boolean }> {
  const running = inFlight.get(key) as Promise<{ value: T; changed: boolean }> | undefined;
  if (running) return running;
  const startedAt = generation;
  const pending = load().then((value) => {
    const before = entries.get(key);
    const changed = !before || JSON.stringify(before.value) !== JSON.stringify(value);
    if (generation === startedAt) {
      entries.set(key, { scope, at: Date.now(), value: structuredClone(value), load });
    }
    return { value, changed };
  });
  inFlight.set(key, pending);
  // Drop only this load: a clear may have let a newer load for the same key start meanwhile
  const drop = () => {
    if (inFlight.get(key) === pending) inFlight.delete(key);
  };
  void pending.then(drop, drop);
  return pending;
}

/** Asks again in the background; on a different answer, screens redraw. Failures keep the shown answer. */
function revalidate(key: string, entry: Entry): void {
  if (inFlight.has(key)) return;
  const startedAt = generation;
  void fetchInto(key, entry.scope, entry.load).then(
    ({ changed }) => {
      if (!changed || generation !== startedAt) return;
      revision += 1;
      for (const listener of listeners) listener();
    },
    () => {},
  );
}

/**
 * Answers `load()` for `path` within `scope` (the access token). A remembered
 * answer younger than {@link API_CACHE_MAX_AGE_MS} is returned at once (and
 * asked for again in the background when older than a moment); otherwise it
 * waits for the network. Failed loads are not cached. Callers get their own
 * copy so editing it does not change the cached answer.
 */
export async function cachedLoad<T>(scope: string, path: string, load: () => Promise<T>): Promise<T> {
  const key = `${scope} ${path}`;
  const hit = entries.get(key);
  if (hit) {
    const age = Date.now() - hit.at;
    if (age < API_CACHE_MAX_AGE_MS) {
      if (age >= REVALIDATE_AFTER_MS) revalidate(key, hit);
      return structuredClone(hit.value) as T;
    }
  }
  const { value } = await fetchInto(key, scope, load);
  return structuredClone(value);
}

/**
 * Asks every remembered list of `scope` again in the background, e.g. when a
 * new notification says the other side changed something. Open screens redraw
 * if an answer changed.
 */
export function refreshApiCache(scope: string): void {
  for (const [key, entry] of entries) {
    if (entry.scope === scope) revalidate(key, entry);
  }
}
