import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

function installDom() {
  const scripts = [];
  const head = {
    appendChild(node) {
      scripts.push(node);
      return node;
    },
    querySelector(sel) {
      if (sel === 'script[data-gyanwire-gis="1"]') {
        return scripts.find((s) => s.dataset?.gyanwireGis === '1') || null;
      }
      return null;
    },
    get innerHTML() {
      return '';
    },
    set innerHTML(_v) {
      scripts.length = 0;
    },
  };
  const document = {
    head,
    querySelector: (...args) => head.querySelector(...args),
    createElement(tag) {
      if (tag !== 'script') throw new Error(`unexpected element ${tag}`);
      const node = {
        src: '',
        async: false,
        defer: false,
        dataset: {},
        onload: null,
        onerror: null,
        addEventListener(type, fn) {
          if (type === 'load') this.onload = fn;
          if (type === 'error') this.onerror = fn;
        },
        setAttribute(name, value) {
          if (name === 'src') this.src = value;
        },
        getAttribute(name) {
          return name === 'src' ? this.src : null;
        },
      };
      return node;
    },
  };
  vi.stubGlobal('document', document);
  vi.stubGlobal('window', globalThis);
  return { scripts, document };
}

describe('googleSignIn.service', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_ID', 'test-client-id.apps.googleusercontent.com');
    installDom();
    delete globalThis.google;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it('rejects when client id is missing', async () => {
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_ID', '');
    vi.resetModules();
    installDom();
    const { requestGoogleSignInAccessToken } = await import('../googleSignIn.service.js');
    await expect(requestGoogleSignInAccessToken()).rejects.toThrow(/VITE_GOOGLE_OAUTH_CLIENT_ID/);
  });

  it('loads GIS script then returns access token', async () => {
    const requestAccessToken = vi.fn();
    const initTokenClient = vi.fn(({ callback }) => {
      queueMicrotask(() => callback({ access_token: 'ya29.from-google' }));
      return { requestAccessToken };
    });

    Object.defineProperty(globalThis, 'google', {
      configurable: true,
      value: { accounts: { oauth2: { initTokenClient } } },
    });

    const { requestGoogleSignInAccessToken } = await import('../googleSignIn.service.js');
    await expect(requestGoogleSignInAccessToken()).resolves.toBe('ya29.from-google');
    expect(initTokenClient).toHaveBeenCalledWith(expect.objectContaining({
      client_id: 'test-client-id.apps.googleusercontent.com',
      scope: expect.stringContaining('openid'),
    }));
    expect(requestAccessToken).toHaveBeenCalled();
  });

  it('surfaces Google error payloads', async () => {
    const initTokenClient = vi.fn(({ callback }) => {
      queueMicrotask(() => callback({
        error: 'access_denied',
        error_description: 'User denied access',
      }));
      return { requestAccessToken: vi.fn() };
    });
    Object.defineProperty(globalThis, 'google', {
      configurable: true,
      value: { accounts: { oauth2: { initTokenClient } } },
    });

    const { requestGoogleSignInAccessToken } = await import('../googleSignIn.service.js');
    await expect(requestGoogleSignInAccessToken()).rejects.toThrow(/access_denied: User denied access/);
  });

  it('injects the GIS script when google oauth2 is absent', async () => {
    const { scripts } = installDom();
    const { requestGoogleSignInAccessToken } = await import('../googleSignIn.service.js');
    const pending = requestGoogleSignInAccessToken();

    expect(scripts.length).toBe(1);
    const script = scripts[0];
    expect(script.src).toBe('https://accounts.google.com/gsi/client');
    expect(script.dataset.gyanwireGis).toBe('1');

    Object.defineProperty(globalThis, 'google', {
      configurable: true,
      value: {
        accounts: {
          oauth2: {
            initTokenClient: ({ callback }) => {
              queueMicrotask(() => callback({ access_token: 'tok' }));
              return { requestAccessToken: vi.fn() };
            },
          },
        },
      },
    });
    script.onload();
    await expect(pending).resolves.toBe('tok');
  });
});
