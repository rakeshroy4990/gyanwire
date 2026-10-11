/**
 * Read an NDJSON response as each line arrives.
 * Does not buffer the body into one JSON value.
 */
export async function readNdjson(response, onLine) {
  if (!response?.body || typeof response.body.getReader !== 'function') {
    const text = await response.text();
    await emitLines(text, onLine);
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    buffer = await drainLines(buffer, onLine);
  }
  buffer += decoder.decode();
  const tail = buffer.trim();
  if (tail) await onLine(JSON.parse(tail));
}

async function emitLines(text, onLine) {
  const leftover = await drainLines(String(text || ''), onLine);
  const tail = leftover.trim();
  if (tail) await onLine(JSON.parse(tail));
}

async function drainLines(buffer, onLine) {
  let newline = buffer.indexOf('\n');
  while (newline >= 0) {
    const line = buffer.slice(0, newline).trim();
    buffer = buffer.slice(newline + 1);
    if (line) await onLine(JSON.parse(line));
    newline = buffer.indexOf('\n');
  }
  return buffer;
}
