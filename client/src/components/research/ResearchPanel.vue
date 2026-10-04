<script setup>
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
]);

function onThoughtsKeydown(event) {
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
        Pick an industry, dump what you are thinking, and get a short list of pages that match what you meant.
      </p>
    </header>

    <ol class="guide-steps" aria-label="How to use Gyanwire">
      <li class="guide-steps__item" :class="{ 'is-done': Boolean(industry) }">
        <span class="guide-steps__num">1</span>
        <span>Pick industry</span>
      </li>
      <li class="guide-steps__item" :class="{ 'is-done': thoughts.trim().length >= 8 }">
        <span class="guide-steps__num">2</span>
        <span>Write thoughts</span>
      </li>
      <li class="guide-steps__item">
        <span class="guide-steps__num">3</span>
        <span>Research</span>
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

    <div class="composer">
      <label class="composer__label" for="research-thoughts">Your research notes</label>
      <textarea
        id="research-thoughts"
        :value="thoughts"
        rows="4"
        maxlength="2000"
        placeholder="Example: CDSCO pathway for Class B SaMD, or NPCI UPI Lite merchant settlement research…"
        @input="emit('update:thoughts', $event.target.value)"
        @keydown="onThoughtsKeydown"
      />

      <div class="composer__footer">
        <p class="composer__note">
          India-first sources. Product news for browsing. Research favors papers, trials, patents, and labs.
        </p>
        <div class="composer__actions">
          <button type="button" class="btn btn--ghost" @click="emit('clear')">Clear</button>
          <button
            type="button"
            class="btn btn--primary"
            :class="{ 'is-loading': isLoading }"
            :disabled="isLoading || limitReached"
            :aria-busy="isLoading"
            @click="emit('search')"
          >
            <span class="btn__label">Research</span>
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

    <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
  </section>
</template>
