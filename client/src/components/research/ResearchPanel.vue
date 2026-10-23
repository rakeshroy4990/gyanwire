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
  ideaLimitReached: { type: Boolean, default: false },
  ideaButtonLabel: { type: String, default: 'Get an idea' },
  limitReached: { type: Boolean, default: false },
  limitMessage: { type: String, default: '' },
  upgradeUrl: { type: String, default: '/pricing' },
  returnKey: { type: String, default: '' },
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
  <section class="search-section" aria-label="News">
    <header class="search-hero">
      <p v-if="greetingName" class="search-hero__hello">Welcome back, {{ greetingName }}.</p>
      <h1 class="search-section__title">Pick a story. Leave with an idea.</h1>
      <p class="search-hero__lede">Filter today’s news, open a story, or ask about one.</p>
    </header>

    <div class="filter-bar">
      <div class="industry-label" id="industry-filters-label">Industry</div>
      <div class="topics topics--industries" role="group" aria-labelledby="industry-filters-label">
        <button
          type="button"
          class="category"
          :class="{ 'is-active': industry === 'All' }"
          :aria-pressed="industry === 'All'"
          @click="emit('select-industry', 'All')"
        >
          All
        </button>
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

      <div v-if="industry && industry !== 'All' && subs.length" class="subpanel">
        <div class="industry-label" id="sub-filters-label">
          Narrow in <span>{{ industry }}</span>
        </div>
        <div class="topics topics--subs" role="group" aria-labelledby="sub-filters-label">
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
    </div>

    <div id="research-chat" class="research-chat">
      <div class="composer">
        <label class="sr-only" for="research-thoughts">
          {{ hasThread ? 'Continue the question' : 'Question' }}
        </label>
        <textarea
          id="research-thoughts"
          :value="thoughts"
          rows="2"
          maxlength="2000"
          :placeholder="hasThread
            ? 'Ask a follow-up…'
            : 'Ask about a story…'"
          @input="emit('update:thoughts', $event.target.value)"
          @keydown="onThoughtsKeydown"
        />

        <div class="composer__footer">
          <p v-if="searchesLeftLabel" class="usage-meter" aria-live="polite">{{ searchesLeftLabel }}</p>
          <div class="composer__actions">
            <button v-if="hasThread" type="button" class="btn btn--ghost" @click="emit('clear')">
              New chat
            </button>
            <button
              type="button"
              class="btn btn--primary"
              :class="{ 'is-loading': isLoading && hasThread }"
              :disabled="(isLoading && hasThread) || limitReached"
              :aria-busy="isLoading && hasThread"
              @click="emit('search')"
            >
              <span class="btn__label">Ask</span>
              <span class="btn__spinner" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>

      <p
        v-if="queryPreview"
        class="query-hint query-hint--live"
        aria-live="polite"
      >
        <span class="query-hint__label">Likely search</span>
        {{ queryPreview }}
      </p>

      <div v-if="limitReached" class="upgrade-prompt" role="alert">
        <p>{{ limitMessage || 'Daily search limit reached.' }}</p>
        <RouterLink class="btn btn--primary upgrade-prompt__cta" :to="upgradeUrl">
          See plans
        </RouterLink>
      </div>

      <p v-if="industry === 'Share Market'" class="disclaimer">Informational only. Not investment advice.</p>
      <p v-else-if="industry === 'Medical'" class="disclaimer">Research aid, not a diagnosis or treatment.</p>
      <p v-else-if="industry === 'Astrology'" class="disclaimer">For cultural context, not prediction.</p>
      <p v-else-if="industry === 'GitHub'" class="disclaimer">Famous open-source projects. Open one, then use Get an idea for business angles.</p>
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>

      <slot />

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
                :return-key="returnKey"
                :idea-limit-reached="ideaLimitReached"
                :idea-button-label="ideaButtonLabel"
                :upgrade-url="upgradeUrl"
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
