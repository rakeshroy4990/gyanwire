import { afterEach, expect, it, vi } from 'vitest';
import { startClickTiming } from '../clickTiming.js';

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

it('logs click→UI once at info and stages at debug', () => {
  const info = vi.spyOn(console, 'info').mockImplementation(() => {});
  const debug = vi.spyOn(console, 'debug').mockImplementation(() => {});
  let now = 1000;
  vi.spyOn(performance, 'now').mockImplementation(() => now);
  vi.stubGlobal('requestAnimationFrame', (cb) => {
    now += 16;
    cb();
    return 1;
  });

  const timing = startClickTiming('ideas');
  now = 1100;
  timing.debug('status:brief');
  now = 1200;
  timing.firstUi('preview');
  timing.firstUi('idea');
  now = 1800;
  timing.done('2 ideas');

  expect(debug.mock.calls.some((c) => String(c[0]).includes('status:brief'))).toBe(true);
  const uiLogs = info.mock.calls.filter((c) => String(c[0]).includes('click→UI'));
  expect(uiLogs).toHaveLength(1);
  expect(String(uiLogs[0][0])).toContain('click→UI');
  expect(info.mock.calls.some((c) => String(c[0]).includes('click→done 800ms'))).toBe(true);
});
