import { expect, it } from 'vitest';
import { readNdjson } from '../newsStream.js';

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

it('emits a complete line before the rest of the body arrives', async () => {
  const { response, push, close } = streamResponse();
  const types = [];
  const pending = readNdjson(response, async (event) => {
    types.push(event.type);
  });

  push('{"type":"open"}\n{"type":"me');
  await waitFor(() => types.length === 1);
  expect(types).toEqual(['open']);

  push('ta","label":"Top searched product news"}\n');
  push('{"type":"item","item":{"title":"A launch","url":"https://a.example/1"}}\n');
  close();
  await pending;
  expect(types).toEqual(['open', 'meta', 'item']);
});

it('awaits each line before reading the next one in the same chunk', async () => {
  const { response, push, close } = streamResponse();
  const order = [];
  const pending = readNdjson(response, async (event) => {
    order.push(`start:${event.type}`);
    await new Promise((resolve) => setTimeout(resolve, 10));
    order.push(`end:${event.type}`);
  });
  push('{"type":"open"}\n{"type":"item","item":{"title":"One"}}\n');
  close();
  await pending;
  expect(order).toEqual(['start:open', 'end:open', 'start:item', 'end:item']);
});
