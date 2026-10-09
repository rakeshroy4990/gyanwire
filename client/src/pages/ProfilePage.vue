<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useAuth } from '../composables/useAuth.js';
import { useProfile } from '../composables/useProfile.js';
import PageSkeleton from '../components/ui/PageSkeleton.vue';

const PERSONAS = [
  { value: 'student', label: 'Student' },
  { value: 'fresher', label: 'Fresher' },
  { value: 'working', label: 'Working' },
  { value: 'self_employed', label: 'Self-employed' },
  { value: 'founder', label: 'Founder' },
];
const GOALS = [
  { value: 'first_income', label: 'First income' },
  { value: 'side_income', label: 'Side income' },
  { value: 'start_business', label: 'Start a business' },
  { value: 'switch_job', label: 'Switch job' },
  { value: 'learn', label: 'Learn' },
];
const INCOME = [
  { value: '0', label: '₹0' },
  { value: 'under_15k', label: 'Under ₹15,000' },
  { value: '15_30', label: '₹15,000–30,000' },
  { value: '30_50', label: '₹30,000–50,000' },
  { value: '50_100', label: '₹50,000–1,00,000' },
  { value: 'over_100', label: 'Over ₹1,00,000' },
];
const CAPITAL = [
  { value: '0', label: '₹0' },
  { value: 'under_5k', label: 'Under ₹5,000' },
  { value: '5_25k', label: '₹5,000–25,000' },
  { value: '25k_1l', label: '₹25,000–1,00,000' },
  { value: 'over_1l', label: 'Over ₹1,00,000' },
];
const INVEST = [
  { value: 5, label: '5% of income' },
  { value: 10, label: '10% of income' },
  { value: 15, label: '15% of income' },
];
const HOURS = [
  { value: 5, label: '5 hours' },
  { value: 10, label: '10 hours' },
  { value: 20, label: '20 hours' },
  { value: 40, label: '40 hours or more' },
];
const INDUSTRIES = ['Share Market', 'IT', 'Medical', 'Space', 'Social Media', 'Gaming', 'Astrology'];

function blankForm() {
  return {
    consent: true,
    persona: 'working',
    goal90d: 'learn',
    incomeBand: '0',
    capitalBand: '0',
    investPct: 10,
    hoursPerWeek: 10,
    industries: ['IT'],
    languages: ['en'],
    state: '',
    city: '',
  };
}

function optionLabel(options, value) {
  const match = options.find((option) => option.value === value);
  return match ? match.label : 'Not set';
}

function withCurrent(options, value) {
  if (value == null || value === '' || options.some((option) => option.value === value)) {
    return options;
  }
  return [{ value, label: String(value) }, ...options];
}

const { isAuthenticated, openLoginPopup } = useAuth();
const { load, save, remove, store, referral, redeem, error, saving } = useProfile();

const loading = ref(true);
const notice = ref('');
const noticeError = ref(false);
const code = ref('');
const redeemCode = ref('');
const editing = ref(false);
const confirmDelete = ref(false);
const copied = ref(false);
const form = reactive(blankForm());

const hasProfile = computed(() => Boolean(store.profile?.persona && store.profile?.consentAt));
const industryOptions = computed(() => {
  const current = form.industries[0];
  return current && !INDUSTRIES.includes(current) ? [current, ...INDUSTRIES] : INDUSTRIES;
});
const personaOptions = computed(() => withCurrent(PERSONAS, form.persona));
const goalOptions = computed(() => withCurrent(GOALS, form.goal90d));
const incomeOptions = computed(() => withCurrent(INCOME, form.incomeBand));
const capitalOptions = computed(() => withCurrent(CAPITAL, form.capitalBand));
const investOptions = computed(() => withCurrent(INVEST, Number(form.investPct)));
const hourOptions = computed(() => withCurrent(HOURS, Number(form.hoursPerWeek)));

const facts = computed(() => {
  const profile = store.profile;
  if (!profile?.persona) return [];
  const rows = [
    ['Persona', optionLabel(PERSONAS, profile.persona)],
    ['90-day goal', optionLabel(GOALS, profile.goal90d)],
    ['Income band', optionLabel(INCOME, profile.incomeBand)],
    ['Capital you can use', optionLabel(CAPITAL, profile.capitalBand)],
    ['Skill budget share', optionLabel(INVEST, Number(profile.investPct))],
    ['Hours per week', optionLabel(HOURS, Number(profile.hoursPerWeek))],
    ['Industry', profile.industries?.[0] || 'Not set'],
  ];
  if (profile.city) rows.push(['City', profile.city]);
  if (profile.state) rows.push(['State', profile.state]);
  return rows;
});

function apply(data) {
  if (!data?.persona) return;
  form.consent = true;
  form.persona = data.persona;
  form.goal90d = data.goal90d || form.goal90d;
  form.incomeBand = data.incomeBand || '0';
  form.capitalBand = data.capitalBand || '0';
  form.investPct = Number(data.investPct) || 10;
  form.hoursPerWeek = Number(data.hoursPerWeek) || 10;
  form.industries = Array.isArray(data.industries) && data.industries.length ? [...data.industries] : ['IT'];
  form.languages = Array.isArray(data.languages) && data.languages.length ? [...data.languages] : ['en'];
  form.city = data.city || '';
  form.state = data.state || '';
}

function setNotice(text, isError = false) {
  notice.value = text;
  noticeError.value = isError;
}

async function refresh() {
  loading.value = true;
  setNotice('');
  try {
    const data = await load();
    apply(data);
    editing.value = !data?.persona;
    const referralData = await referral();
    code.value = referralData?.code || '';
  } catch (err) {
    setNotice(err.message, true);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (isAuthenticated.value) refresh();
  else loading.value = false;
});

watch(isAuthenticated, (signedIn, wasSignedIn) => {
  if (signedIn && !wasSignedIn) refresh();
  if (!signedIn) loading.value = false;
});

async function submitProfile() {
  setNotice('');
  confirmDelete.value = false;
  try {
    const data = await save({
      ...form,
      consent: true,
      industries: form.industries[0] ? [form.industries[0]] : ['IT'],
      city: form.city.trim(),
      state: form.state.trim(),
    });
    apply(data);
    editing.value = false;
    setNotice('Profile saved. Ideas and plans will use these answers.');
  } catch (err) {
    setNotice(err.message, true);
  }
}

async function submitRedeem() {
  setNotice('');
  const next = redeemCode.value.trim();
  if (next.length < 6) {
    setNotice('Enter the full referral code.', true);
    return;
  }
  try {
    await redeem(next);
    redeemCode.value = '';
    setNotice('Referral applied.');
  } catch (err) {
    setNotice(err.message, true);
  }
}

async function copyCode() {
  if (!code.value) return;
  try {
    await navigator.clipboard.writeText(code.value);
    copied.value = true;
  } catch {
    setNotice('Could not copy the code. Select it and copy it manually.', true);
  }
}

async function onDelete() {
  if (!confirmDelete.value) {
    confirmDelete.value = true;
    return;
  }
  setNotice('');
  try {
    await remove();
    Object.assign(form, blankForm());
    editing.value = true;
    confirmDelete.value = false;
    setNotice('Profile data deleted. Your account is still here.');
  } catch (err) {
    setNotice(err.message, true);
  }
}
</script>

<template>
  <main class="account-page">
    <header class="account-page__hero">
      <p class="account-page__kicker">Account</p>
      <h1>Profile</h1>
      <p>
        These answers shape the ideas, budgets, and weekly plans we suggest.
        We store a band, never an exact salary.
      </p>
    </header>

    <section v-if="!isAuthenticated" class="account-card">
      <h2>Sign in to see your profile</h2>
      <p>Your persona, goal, and referral code stay on your account.</p>
      <button type="button" class="btn btn--primary" @click="openLoginPopup()">Sign in</button>
    </section>

    <template v-else>
      <PageSkeleton v-if="loading" variant="facts" :rows="6" label="Loading profile" />
      <template v-else>
      <p v-if="notice || error" class="account-notice" :class="{ 'form-error': noticeError || (!notice && error) }" role="status">
        {{ notice || error }}
      </p>

      <section v-if="hasProfile && !editing" class="account-card">
        <h2>Your answers</h2>
        <dl class="account-facts">
          <template v-for="[label, value] in facts" :key="label">
            <dt>{{ label }}</dt>
            <dd>{{ value }}</dd>
          </template>
        </dl>
        <button type="button" class="btn btn--primary" @click="editing = true; confirmDelete = false">
          Edit profile
        </button>
      </section>

      <form v-if="!hasProfile || editing" class="account-card account-form" @submit.prevent="submitProfile">
        <h2>{{ hasProfile ? 'Update profile' : 'Set up your profile' }}</h2>
        <p>Pick the closest band. You can change this whenever your time or money changes.</p>

        <label class="auth-field">
          <span class="auth-field__label">Persona</span>
          <select v-model="form.persona">
            <option v-for="option in personaOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label class="auth-field">
          <span class="auth-field__label">90-day goal</span>
          <select v-model="form.goal90d">
            <option v-for="option in goalOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label class="auth-field">
          <span class="auth-field__label">Income band</span>
          <select v-model="form.incomeBand">
            <option v-for="option in incomeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <span class="auth-field__hint">A range, not an exact amount.</span>
        </label>
        <label class="auth-field">
          <span class="auth-field__label">Capital you can use</span>
          <select v-model="form.capitalBand">
            <option v-for="option in capitalOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label class="auth-field">
          <span class="auth-field__label">Share of income for skills</span>
          <select v-model.number="form.investPct">
            <option v-for="option in investOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label class="auth-field">
          <span class="auth-field__label">Hours per week</span>
          <select v-model.number="form.hoursPerWeek">
            <option v-for="option in hourOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label class="auth-field">
          <span class="auth-field__label">Industry</span>
          <select v-model="form.industries[0]">
            <option v-for="name in industryOptions" :key="name" :value="name">{{ name }}</option>
          </select>
        </label>
        <label class="auth-field">
          <span class="auth-field__label">City</span>
          <input v-model="form.city" type="text" maxlength="80" autocomplete="address-level2" placeholder="Optional">
          <span class="auth-field__hint">City changes which local schemes and customers we suggest.</span>
        </label>
        <label class="auth-field">
          <span class="auth-field__label">State</span>
          <input v-model="form.state" type="text" maxlength="80" autocomplete="address-level1" placeholder="Optional">
        </label>

        <div class="account-actions">
          <button class="btn btn--primary" type="submit" :disabled="saving">
            {{ saving ? 'Saving…' : 'Save profile' }}
          </button>
          <button v-if="hasProfile" type="button" class="btn btn--ghost" @click="editing = false">
            Cancel
          </button>
        </div>
      </form>

      <section class="account-card">
        <h2>Referral</h2>
        <p>Share this code. When someone redeems it, the credit applies to their account.</p>
        <p class="account-code">{{ code || 'Your code will appear here after sign-in.' }}</p>
        <button type="button" class="btn btn--ghost" :disabled="!code" @click="copyCode">
          {{ copied ? 'Copied' : 'Copy code' }}
        </button>
        <form class="account-form" @submit.prevent="submitRedeem">
          <label class="auth-field">
            <span class="auth-field__label">Redeem a code</span>
            <input
              v-model="redeemCode"
              type="text"
              maxlength="32"
              autocomplete="off"
              spellcheck="false"
              placeholder="Paste a referral code"
            >
          </label>
          <button class="btn btn--primary" type="submit">Redeem</button>
        </form>
      </section>

      <section v-if="hasProfile" class="account-card">
        <h2>Delete profile data</h2>
        <p>This removes your persona, goals, and skills. Your sign-in stays.</p>
        <button type="button" class="btn btn--ghost" @click="onDelete">
          {{ confirmDelete ? 'Click again to delete' : 'Delete profile data' }}
        </button>
      </section>
      </template>
    </template>
  </main>
</template>
