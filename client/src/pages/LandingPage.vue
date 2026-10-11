<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import cssUrl from '../vendor/scrollcraft.css?url';
import '../vendor/scrollcraft.js';
import { attachWires } from './landingWires.js';
import './landing.css';

const TOKENS = `:root {
  --sc-canvas: var(--canvas);
  --sc-surface: var(--surface);
  --sc-ink: var(--ink);
  --sc-ink-soft: var(--ink-soft);
  --sc-accent: var(--accent);
  --sc-accent-ink: var(--accent-ink);
  --sc-font-display: var(--font-display);
  --sc-font-text: var(--font-text);
}
html { scroll-behavior: auto; }`;

const router = useRouter();
const root = ref(null);
const question = ref('');
let api = null;
let stopWires = null;
let link = null;
let tokens = null;
let previousTitle = '';

function start() {
  const text = question.value.trim().slice(0, 2000);
  router.push(text ? { name: 'research', query: { q: text } } : { name: 'research' });
}

onMounted(async () => {
  previousTitle = document.title;
  document.title = 'Gyanwire · Live research';
  await nextTick();
  link = document.createElement('link');
  link.rel = 'stylesheet';
  link.href = cssUrl;
  tokens = document.createElement('style');
  tokens.setAttribute('data-sc-tokens', '');
  tokens.textContent = TOKENS;
  link.addEventListener('load', () => api?.layout());
  document.head.append(link, tokens);
  api = window.ScrollCraft.mount(root.value);
  stopWires = attachWires(root.value);
});

onUnmounted(() => {
  stopWires?.();
  stopWires = null;
  api?.destroy();
  api = null;
  link?.remove();
  tokens?.remove();
  if (previousTitle) document.title = previousTitle;
});
</script>

<template>
  <div ref="root" class="gw-page">
    <span data-sc-progress></span>
    <div class="sc-grain" aria-hidden="true"></div>
    <svg class="gw-wires" viewBox="0 0 80 800" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <path class="gw-wire gw-wire--pile" pathLength="1" d="M58 36 C 24 180, 18 340, 52 520" />
      <path class="gw-wire gw-wire--why" pathLength="1" d="M36 48 C 70 210, 12 390, 46 600" />
      <path class="gw-wire gw-wire--idea" pathLength="1" d="M22 40 C 16 250, 68 430, 40 700" />
    </svg>

    <main>
      <section class="gw-act" data-sc-act="pin" data-sc-span="1.8">
        <div data-sc-stage class="gw-stage">
          <article class="gw-app">
            <header class="gw-bar">
              <p class="gw-mark">Gyanwire</p>
              <p class="gw-sample">Sample</p>
            </header>
            <div class="gw-status-slot">
              <p class="gw-status" data-sc-cue="0 0.5 0">Query is in the field.</p>
              <p class="gw-status" data-sc-cue="0.38 1">Findings have not arrived.</p>
            </div>
            <p class="gw-meta">Medical</p>
            <h1 class="gw-query">Labs told to file calibration logs by 1 November</h1>
          </article>
        </div>
      </section>

      <section class="gw-act" data-sc-act="pan" data-sc-span="3" data-wire-source="pile">
        <div data-sc-stage class="gw-stage">
          <div class="gw-rail" data-sc-pan="0.04">
            <article class="gw-link">
              <header class="gw-bar">
                <p class="gw-mark">Gyanwire</p>
                <p class="gw-sample">Sample</p>
              </header>
              <p class="gw-meta">Medical</p>
              <h2 class="gw-query">Labs told to file calibration logs by 1 November</h2>
              <p class="gw-missing">No why-line yet</p>
            </article>
            <article class="gw-link">
              <header class="gw-bar">
                <p class="gw-mark">Gyanwire</p>
                <p class="gw-sample">Sample</p>
              </header>
              <p class="gw-meta">Medical</p>
              <h2 class="gw-query">Clinic owners who miss the date can be asked to pause testing</h2>
              <p class="gw-missing">No why-line yet</p>
            </article>
          </div>
        </div>
      </section>

      <section class="gw-act gw-act--flow" data-sc-act="flow" data-wire-source="why">
        <article class="gw-app">
          <header class="gw-bar">
            <p class="gw-mark">Gyanwire</p>
            <p class="gw-sample">Sample</p>
          </header>
          <p class="gw-meta">Medical</p>
          <h2 class="gw-query">Labs told to file calibration logs by 1 November</h2>
          <div class="gw-why" data-sc-reveal="up" data-sc-reveal-at="0.18 0.78">
            <p class="gw-why__label">Why</p>
            <p class="gw-why__text">Lab owners have a dated filing task and will pay to avoid a pause in testing.</p>
          </div>
        </article>
      </section>

      <section class="gw-act" data-sc-act="pin" data-sc-span="4.6" data-wire-source="idea">
        <div data-sc-stage class="gw-stage">
          <article class="gw-app">
            <header class="gw-bar">
              <p class="gw-mark">Gyanwire</p>
              <p class="gw-sample">Sample</p>
            </header>
            <p class="gw-meta">Medical</p>
            <h2 class="gw-query" data-sc-cue="0 0.34 0">Labs told to file calibration logs by 1 November</h2>
            <div class="gw-idea">
              <p class="gw-sample">Sample</p>
              <div class="gw-idea-slot">
                <h2 class="gw-idea__title" data-sc-cue="0.18 0.55">File calibration logs for labs before 1 November</h2>
                <p data-sc-cue="0.42 0.78">A done-for-you filing service for labs that will miss the date.</p>
                <p data-sc-cue="0.64 1"><span class="gw-label">Why now:</span> The window is days, and the notice is already published. ₹6,000 per lab.</p>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section id="start" class="gw-act gw-act--flow" data-sc-act="flow" data-wire-source="close">
        <article class="gw-app gw-app--close">
          <header class="gw-bar">
            <p class="gw-mark">Gyanwire</p>
          </header>
          <p class="gw-close-line" data-sc-cue="0">The field is empty. This one is yours.</p>
          <form class="gw-form" @submit.prevent="start">
            <label for="gw-question">Research question</label>
            <textarea
              id="gw-question"
              v-model="question"
              name="q"
              rows="4"
              maxlength="2000"
              placeholder="Ask about a news item, a market, or a messy note"
            ></textarea>
            <button class="gw-submit" type="submit">Start research</button>
          </form>
          <nav class="gw-legal" aria-label="Legal">
            <RouterLink to="/terms">Terms</RouterLink>
            <RouterLink to="/privacy">Privacy</RouterLink>
            <RouterLink to="/refund">Refunds</RouterLink>
            <RouterLink to="/contact">Contact</RouterLink>
          </nav>
        </article>
      </section>
    </main>
  </div>
</template>
