import { onBeforeUnmount, ref, watch } from 'vue';

export function prefersReducedMotion() {
  return typeof window !== 'undefined'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function easeOutCubic(t) {
  return 1 - (1 - t) ** 3;
}

/**
 * Animates a numeric ref from 0 (or current) to target with requestAnimationFrame.
 */
export function useTween(durationMs = 1100) {
  const value = ref(0);
  let frame = 0;
  let start = 0;
  let from = 0;
  let to = 0;
  let running = false;

  function cancel() {
    if (frame) cancelAnimationFrame(frame);
    frame = 0;
    running = false;
  }

  function tick(now) {
    const elapsed = now - start;
    const t = Math.min(1, elapsed / durationMs);
    value.value = from + (to - from) * easeOutCubic(t);
    if (t < 1) {
      frame = requestAnimationFrame(tick);
      return;
    }
    value.value = to;
    running = false;
    frame = 0;
  }

  function play(target) {
    cancel();
    to = Number(target) || 0;
    if (prefersReducedMotion() || durationMs <= 0) {
      value.value = to;
      return;
    }
    from = value.value;
    start = performance.now();
    running = true;
    frame = requestAnimationFrame(tick);
  }

  function snap(target) {
    cancel();
    value.value = Number(target) || 0;
  }

  onBeforeUnmount(cancel);

  return { value, play, snap, cancel, running: () => running };
}

export function useTweenGroup(keys, durationMs = 1100) {
  const tweens = Object.fromEntries(keys.map((key) => [key, useTween(durationMs)]));

  function playAll(targets) {
    for (const key of keys) {
      tweens[key].play(targets[key] ?? 0);
    }
  }

  function snapAll(targets) {
    for (const key of keys) {
      tweens[key].snap(targets[key] ?? 0);
    }
  }

  function watchTargets(getTargets) {
    watch(
      getTargets,
      (targets) => {
        if (!targets) {
          snapAll(Object.fromEntries(keys.map((k) => [k, 0])));
          return;
        }
        playAll(targets);
      },
      { immediate: true },
    );
  }

  return { tweens, playAll, snapAll, watchTargets };
}
