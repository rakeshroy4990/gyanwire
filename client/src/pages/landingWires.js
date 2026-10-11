export function attachWires(root) {
  const svg = root.querySelector('.gw-wires');
  if (!svg) return () => {};
  const sources = [...root.querySelectorAll('[data-wire-source]')];
  let frame = 0;
  let dead = false;

  const tick = () => {
    if (dead) return;
    for (const el of sources) {
      const name = el.getAttribute('data-wire-source');
      const raw = getComputedStyle(el).getPropertyValue('--sc-p').trim();
      const value = raw === '' ? 0 : Number(raw);
      svg.style.setProperty(`--wire-${name}`, Number.isFinite(value) ? String(value) : '0');
    }
    frame = requestAnimationFrame(tick);
  };

  frame = requestAnimationFrame(tick);
  return () => {
    dead = true;
    if (frame) cancelAnimationFrame(frame);
  };
}
