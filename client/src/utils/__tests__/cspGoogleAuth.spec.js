import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('CSP allows Google Identity Services', () => {
  it('index.html meta CSP allows Google and omits header-only frame-ancestors', () => {
    const html = readFileSync(resolve('client/index.html'), 'utf8');
    const match = html.match(/Content-Security-Policy" content="([^"]+)"/);
    expect(match).toBeTruthy();
    const csp = match[1];
    expect(csp).toMatch(/script-src[^;]*https:\/\/accounts\.google\.com/);
    expect(csp).toMatch(/frame-src[^;]*https:\/\/accounts\.google\.com/);
    // Browsers ignore frame-ancestors in <meta>; keep it only on HTTP headers.
    expect(csp).not.toMatch(/frame-ancestors/);
  });

  it('firebase hosting CSP keeps frame-ancestors and Google sources', () => {
    const firebase = JSON.parse(readFileSync(resolve('firebase.json'), 'utf8'));
    const allHeaders = firebase.hosting.headers.flatMap((block) => block.headers);
    const csp = allHeaders.find((h) => h.key === 'Content-Security-Policy');
    const xfo = allHeaders.find((h) => h.key === 'X-Frame-Options');
    expect(csp.value).toMatch(/script-src[^;]*https:\/\/accounts\.google\.com/);
    expect(csp.value).toMatch(/frame-src[^;]*https:\/\/accounts\.google\.com/);
    expect(csp.value).toMatch(/frame-ancestors 'none'/);
    expect(xfo.value).toBe('DENY');
  });
});
