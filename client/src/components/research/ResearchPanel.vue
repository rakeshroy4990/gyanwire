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
  <section class="search-section" aria-label="Research search">
    <h1 class="search-section__title">What are you researching?</h1>
    <p class="search-section__lede">
      <template v-if="greetingName">
        Welcome back, {{ greetingName }}. Click an industry for product news, or write a research question.
      </template>
      <template v-else>
        Click an industry for product news. Or write a research question and dig deeper.
      </template>
    </p>

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
        Top searched in <span>{{ industry }}</span>
      </div>
      <div class="topics topics--subs" role="group" aria-label="Sub combinations">
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
      <textarea
        :value="thoughts"
        rows="4"
        maxlength="2000"
        placeholder="Example: NVIDIA inference chips for edge devices, or Instagram Reels ranking research…"
        aria-label="Research notes"
        @input="emit('update:thoughts', $event.target.value)"
        @keydown="onThoughtsKeydown"
      />

      <div class="composer__footer">
        <p class="composer__note">
          India-first results. News highlights real products. Research favors papers, trials, patents, and labs.
        </p>
        <div class="composer__actions">
          <button type="button" class="btn btn--ghost" @click="emit('clear')">Clear</button>
          <button
            type="button"
            class="btn btn--primary"
            :class="{ 'is-loading': isLoading }"
            :disabled="isLoading"
            :aria-busy="isLoading"
            @click="emit('search')"
          >
            <span class="btn__label">Research</span>
            <span class="btn__spinner" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>

    <p class="query-hint">{{ queryPreview }}</p>
    <p v-if="searchesLeftLabel" class="usage-meter" aria-live="polite">{{ searchesLeftLabel }}</p>
    <div v-if="limitReached" class="upgrade-prompt" role="alert">
      <p>{{ limitMessage || 'Daily search limit reached.' }}</p>
      <a class="btn btn--primary upgrade-prompt__cta" :href="upgradeUrl">Upgrade</a>
    </div>
    <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
  </section>
</template>
