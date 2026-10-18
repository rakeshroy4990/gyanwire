/**
 * Click → UI timing. Summary lines use console.info (always visible).
 * Per-stage lines use console.debug (Chrome: Verbose / “All levels”).
 * Cheap: only numbers and short strings; no JSON stringify on the hot path.
 */

export function startClickTiming(label) {
  const t0 = performance.now();
  const marks = Object.create(null);
  let firstUiLogged = false;

  function elapsed() {
    return Math.round(performance.now() - t0);
  }

  function mark(name) {
    if (marks[name] != null) return marks[name];
    const ms = elapsed();
    marks[name] = ms;
    return ms;
  }

  function debug(stage, extra) {
    if (extra == null || extra === '') {
      console.debug(`[${label}] +${elapsed()}ms ${stage}`);
      return;
    }
    console.debug(`[${label}] +${elapsed()}ms ${stage}`, extra);
  }

  function info(message) {
    console.info(`[${label}] ${message}`);
  }

  /**
   * First meaningful paint after click. Waits for Vue flush + one frame.
   * Call once when the first idea/card is in reactive state.
   */
  function firstUi(detail) {
    if (firstUiLogged) return;
    firstUiLogged = true;
    const atState = mark('firstUiState');
    const paint = () => {
      const atPaint = mark('firstUi');
      info(`click→UI ${atPaint}ms${detail ? ` (${detail})` : ''} [state ${atState}ms]`);
    };
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(paint);
    } else {
      paint();
    }
  }

  function done(detail) {
    const ms = mark('done');
    info(`click→done ${ms}ms${detail ? ` (${detail})` : ''}`);
  }

  function fail(err) {
    const ms = mark('done');
    info(`click→error ${ms}ms: ${err?.message || err || 'failed'}`);
  }

  debug('start');
  return { elapsed, mark, debug, info, firstUi, done, fail, marks };
}
