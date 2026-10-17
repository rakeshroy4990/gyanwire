import { afterEach, expect, it, vi } from 'vitest';
import { createActionPlan } from '../actionPlan.service.js';

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

it('fires the first plan callback before the rest of the body arrives', async () => {
  const { response, push, close } = streamResponse();
  vi.stubGlobal('fetch', vi.fn(async () => response));

  const events = [];
  const pending = createActionPlan(
    { newsUrl: 'https://example.com/a', title: 'A launch' },
    async (event) => {
      events.push(event);
    },
  );

  push('{"type":"open","intent":"action_plan"}\n');
  push('{"type":"plan","success":true,"plan":{"planId":"p1","buildStatus":"building","label":"Quick short"}}\n');
  await waitFor(() => events.some((event) => event.type === 'plan'));
  expect(events.filter((event) => event.type === 'plan')).toHaveLength(1);
  expect(events.at(-1).plan.planId).toBe('p1');
  expect(events.at(-1).plan.buildStatus).toBe('building');

  push('{"type":"status","stage":"brief"}\n');
  push('{"type":"plan","success":true,"plan":{"planId":"p1","buildStatus":"ready","label":"Quick short on X"}}\n');
  push('{"type":"done","success":true}\n');
  close();
  const plan = await pending;
  expect(plan.planId).toBe('p1');
  expect(plan.buildStatus).toBe('ready');
  expect(events.filter((event) => event.type === 'status')).toHaveLength(1);
});
