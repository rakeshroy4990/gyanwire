import { afterEach, expect, it, vi } from 'vitest';
import { fetchNewsIdeas } from '../newsIdeas.service.js';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function streamResponse() {
  let push;
  let close;
  const stream = new ReadableStream({
    start(controller) {
      push = (text) => controller.enqueue(new TextEncoder().encode(text));
      close = () => controller.close();
    },
  });
  const response = new Response(stream, {
    status: 200,
    headers: { 'Content-Type': 'application/x-ndjson' },
  });
  return { response, push, close };
}

async function waitFor(check) {
  const started = Date.now();
  while (!check()) {
    if (Date.now() - started > 1000) throw new Error('timed out waiting for a stream line');
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
}

it('fires the first item callback before the rest of the body arrives', async () => {
  const { response, push, close } = streamResponse();
  vi.stubGlobal('fetch', vi.fn(async () => response));

  const events = [];
  const pending = fetchNewsIdeas('news-1', async (event) => {
    events.push(event);
  });

  push('{"type":"open","intent":"ideas"}\n');
  push('{"type":"status","stage":"brief"}\n');
  push('{"type":"meta","success":true,"headline":"A launch","brief":{"headline":"A launch"}}\n');
  push('{"type":"status","stage":"angles"}\n');
  push('{"type":"item","item":{"id":"ni-1","title":"First idea"}}\n');
  await waitFor(() => events.some((event) => event.type === 'item'));
  expect(events.filter((event) => event.type === 'item')).toHaveLength(1);
  expect(events.at(-1).item.title).toBe('First idea');

  push('{"type":"item","item":{"id":"ni-2","title":"Second idea"}}\n');
  push('{"type":"research","status":"started"}\n');
  push('{"type":"done","success":true,"returned":2}\n');
  close();
  const frame = await pending;
  expect(frame.ideas.map((row) => row.title)).toEqual(['First idea', 'Second idea']);
  expect(frame.headline).toBe('A launch');
  expect(events.some((event) => event.type === 'research' && event.status === 'started')).toBe(true);
});
