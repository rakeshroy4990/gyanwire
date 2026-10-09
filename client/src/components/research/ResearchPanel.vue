<script setup>
import FindingsSkeleton from './FindingsSkeleton.vue';
import ResultsPanel from './ResultsPanel.vue';
import CitedBrief from './CitedBrief.vue';

defineProps({
  greetingName: { type: String, default: '' },
  catalog: { type: Array, required: true },
  industry: { type: String, default: null },
  subcategory: { type: String, default: null },
  subs: { type: Array, default: () => [] },
  thoughts: { type: String, default: '' },
  queryPreview: { type: String, default: '' },
  formError: { type: String, default: '' },
  isLoading: { type: Boolean, default: false },
  thread: { type: Array, default: () => [] },
  hasThread: { type: Boolean, default: false },
  searchesLeftLabel: { type: String, default: '' },
  limitReached: { type: Boolean, default: false },
  limitMessage: { type: String, default: '' },
  upgradeUrl: { type: String, default: '/pricing' },
});

const emit = defineEmits([
  'update:thoughts',
  'select-industry',
  'select-sub',
  'search',
  'clear',
  'idea',
  'feedback',
  'claim',
]);

function onThoughtsKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    emit('search');
    return;
  }
  if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
    event.preventDefault();
    emit('search');
  }
}
</script>

<template>
  <section class="search-section" aria-label="Research workspace">
    <header class="search-hero">
      <p class="search-hero__brand">Gyanwire</p>
      <h1 class="search-section__title">
        <template v-if="greetingName">Welcome back, {{ greetingName }}.</template>
        <template v-else>Messy notes in. Ranked findings out.</template>
      </h1>
      <p class="search-section__lede">
        Research like a chat: keep asking, keep the thread, and findings stay under each question.
      </p>
    </header>

    <ol class="guide-steps" aria-label="How to use Gyanwire">
      <li class="guide-steps__item" :class="{ 'is-done': Boolean(industry) }">
        <span class="guide-steps__num">1</span>
        <span>Pick industry</span>
      </li>
      <li class="guide-steps__item" :class="{ 'is-done': thoughts.trim().length >= 8 || hasThread }">
        <span class="guide-steps__num">2</span>
        <span>Ask in chat</span>
      </li>
      <li class="guide-steps__item" :class="{ 'is-done': hasThread }">
        <span class="guide-steps__num">3</span>
        <span>Read findings</span>
      </li>
    </ol>

    <div class="industry-label">Research industries</div>
    <div class="topics topics--industries" role="group" aria-label="Research industries">
      <button
        v-for="item in catalog"
        :key="item.name"
        type="button"
        class="category"
        :class="{ 'is-active': industry === item.name }"
        :aria-pressed="industry === item.name"
        @click="emit('select-industry', item.name)"
      >
        {{ item.name }}
      </button>
    </div>

    <div v-if="industry" class="subpanel">
      <div class="industry-label">
        Narrow in <span>{{ industry }}</span>
      </div>
      <div class="topics topics--subs" role="group" aria-label="Sub topics">
        <button
          v-for="name in subs"
          :key="name"
          type="button"
          class="category"
          :class="{ 'is-active': subcategory === name }"
          :aria-pressed="subcategory === name"
          @click="emit('select-sub', name)"
        >
          {{ name }}
        </button>
      </div>
    </div>

    <div id="research-chat" class="research-chat">
      <div class="composer">
        <label class="composer__label" for="research-thoughts">
          {{ hasThread ? 'Continue the research chat' : 'Start a research chat' }}
        </label>
        <textarea
          id="research-thoughts"
          :value="thoughts"
          rows="3"
          maxlength="2000"
          :placeholder="hasThread
            ? 'Ask a follow-up… (Enter to send, Shift+Enter for a new line)'
            : 'Example: CDSCO pathway for Class B SaMD… (Enter to send)'"
          @input="emit('update:thoughts', $event.target.value)"
          @keydown="onThoughtsKeydown"
        />

        <div class="composer__footer">
          <p class="composer__note">
            Your questions stay in this thread. Findings attach under each one.
          </p>
          <div class="composer__actions">
            <button type="button" class="btn btn--ghost" @click="emit('clear')">
              New chat
            </button>
            <button
              type="button"
              class="btn btn--primary"
              :class="{ 'is-loading': isLoading }"
              :disabled="isLoading || limitReached"
              :aria-busy="isLoading"
              @click="emit('search')"
            >
              <span class="btn__label">{{ hasThread ? 'Ask' : 'Research' }}</span>
              <span class="btn__spinner" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>

      <p
        class="query-hint"
        :class="{ 'query-hint--live': Boolean(queryPreview) }"
        aria-live="polite"
      >
        <template v-if="queryPreview">
          <span class="query-hint__label">Likely search</span>
          {{ queryPreview }}
        </template>
        <template v-else-if="hasThread">
          Type a follow-up above. Earlier questions and findings stay below.
        </template>
        <template v-else>
          Start typing. A live search preview appears here before you commit.
        </template>
      </p>

      <p v-if="searchesLeftLabel" class="usage-meter" aria-live="polite">{{ searchesLeftLabel }}</p>

      <div v-if="limitReached" class="upgrade-prompt" role="alert">
        <p>{{ limitMessage || 'Daily search limit reached.' }}</p>
        <RouterLink class="btn btn--primary upgrade-prompt__cta" :to="upgradeUrl">
          See plans
        </RouterLink>
      </div>

      <p v-if="industry === 'Share Market'" class="disclaimer">Informational only. Not investment advice or a recommendation to buy or sell.</p>
      <p v-else-if="industry === 'Medical'" class="disclaimer">Research aid, not a diagnosis or treatment.</p>
      <p v-else-if="industry === 'Astrology'" class="disclaimer">Cultural and educational context, not a health or financial prediction.</p>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>

      <div v-if="hasThread" class="chat-thread" aria-live="polite">
        <article
          v-for="turn in thread"
          :id="`turn-${turn.id}`"
          :key="turn.id"
          class="chat-turn"
        >
          <div class="chat-bubble chat-bubble--user">
            <p class="chat-bubble__meta">
              You
              <span v-if="turn.industry">
                · {{ turn.industry }}<template v-if="turn.subcategory"> · {{ turn.subcategory }}</template>
              </span>
            </p>
            <p class="chat-bubble__text">{{ turn.userText }}</p>
          </div>

          <div class="chat-bubble chat-bubble--assistant">
            <p class="chat-bubble__meta">Gyanwire</p>
            <p v-if="turn.disclaimer" class="disclaimer">{{ turn.disclaimer }}</p>
            <FindingsSkeleton
              v-if="turn.status === 'pending'"
              embedded
              :rows="3"
            />
            <p v-else-if="turn.status === 'error'" class="chat-bubble__text chat-bubble__text--error">
              {{ turn.error || 'That search failed.' }}
            </p>
            <template v-else>
              <ResultsPanel
                embedded
                heading="Findings"
                show-ideas
                :results="turn.results"
                :results-sub="turn.resultsSub"
                :show-empty="turn.results.length === 0"
                :show-results="turn.results.length > 0"
                :is-loading="turn.status === 'streaming'"
                :empty-message="'No strong findings for this question.'"
                @idea="emit('idea', $event)"
                @feedback="emit('feedback', $event)"
              />
              <CitedBrief v-if="turn.brief?.summary" :brief="turn.brief" @claim="emit('claim', $event)" />
            </template>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>
