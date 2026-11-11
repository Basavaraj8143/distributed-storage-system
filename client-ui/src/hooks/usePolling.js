import { useEffect, useRef } from "react";

/**
 * Calls `callback` every `intervalMs` while the page is mounted and the browser tab is visible.
 *
 * - The latest callback is always used, so it can safely close over changing state.
 * - `immediate` (default true) also runs it once on mount.
 * - `enabled` (default true) lets a page pause polling.
 */
export function usePolling(callback, intervalMs, { immediate = true, enabled = true } = {}) {
  const latest = useRef(callback);

  useEffect(() => {
    latest.current = callback;
  }, [callback]);

  useEffect(() => {
    if (!enabled) return undefined;

    if (immediate) latest.current();

    const id = setInterval(() => {
      if (!document.hidden) latest.current();
    }, intervalMs);

    return () => clearInterval(id);
  }, [intervalMs, immediate, enabled]);
}
