import { useSyncExternalStore } from "react";
import { getApiCacheRevision, subscribeApiCache } from "../api/cache";

/**
 * A number that changes when a remembered tab list got a newer answer in the
 * background (ADR 0077). List hooks put it in their load effect's dependencies
 * so the open screen redraws with the new list; the effect's call answers from
 * the cache at once, so this does not show loading again.
 */
export function useCachedListRevision(): number {
  return useSyncExternalStore(subscribeApiCache, getApiCacheRevision, getApiCacheRevision);
}
