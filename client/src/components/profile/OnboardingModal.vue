<script setup>
import { reactive, ref } from 'vue';
import { useProfile } from '../../composables/useProfile.js';

const emit = defineEmits(['done', 'close']);
const { save, error, saving } = useProfile();
const step = ref(0);
const showMore = ref(false);
const form = reactive({
  consent: true,
  persona: 'student',
  goal90d: 'learn',
  incomeBand: '0',
  capitalBand: '0',
  investPct: 10,
  hoursPerWeek: 10,
  industries: ['IT'],
  languages: ['en'],
  locationTier: '',
  state: '',
  city: '',
  riskAppetite: 'low',
  education: '',
  assets: [],
  constraints: [],
  skills: [],
});

const steps = ['Persona', 'Goal', 'Income', 'Invest %', 'Hours', 'Industries'];

async function next() {
  if (step.value < steps.length - 1) {
    step.value += 1;
    return;
  }
  await save({ ...form, consent: true });
  emit('done');
}
</script>

<template>
  <div class="auth-modal" role="presentation">
    <div class="auth-modal__backdrop" @click="emit('close')" />
    <form
      class="auth-modal__panel auth-modal__panel--scroll"
      role="dialog"
      aria-modal="true"
      aria-labelledby="profileTitle"
      @submit.prevent="next"
    >
      <button type="button" class="auth-modal__close" aria-label="Close" @click="emit('close')">
        <span aria-hidden="true">×</span>
      </button>
      <div class="auth-modal__brand" aria-hidden="true">
        <span class="auth-modal__mark" />
      </div>
      <header class="auth-modal__intro">
        <h2 id="profileTitle">About a minute</h2>
        <p class="auth-modal__lede">Step {{ step + 1 }} of {{ steps.length }} · {{ steps[step] }}</p>
      </header>
      <label v-if="step === 0" class="auth-field">
        <span class="auth-field__label">Persona</span>
        <select v-model="form.persona">
          <option value="student">Student</option>
          <option value="fresher">Fresher</option>
          <option value="working">Working</option>
          <option value="self_employed">Self-employed</option>
          <option value="founder">Founder</option>
        </select>
      </label>
      <label v-else-if="step === 1" class="auth-field">
        <span class="auth-field__label">90-day goal</span>
        <select v-model="form.goal90d">
          <option value="first_income">First income</option>
          <option value="side_income">Side income</option>
          <option value="start_business">Start a business</option>
          <option value="switch_job">Switch job</option>
          <option value="learn">Learn</option>
        </select>
      </label>
      <label v-else-if="step === 2" class="auth-field">
        <span class="auth-field__label">Income band</span>
        <select v-model="form.incomeBand">
          <option value="0">₹0</option>
          <option value="under_15k">Under ₹15k</option>
          <option value="15_30">₹15–30k</option>
          <option value="30_50">₹30–50k</option>
          <option value="50_100">₹50k–1L</option>
          <option value="over_100">Over ₹1L</option>
        </select>
      </label>
      <label v-else-if="step === 3" class="auth-field">
        <span class="auth-field__label">Skill budget share</span>
        <select v-model.number="form.investPct">
          <option :value="5">5%</option>
          <option :value="10">10%</option>
          <option :value="15">15%</option>
        </select>
      </label>
      <label v-else-if="step === 4" class="auth-field">
        <span class="auth-field__label">Hours per week</span>
        <select v-model.number="form.hoursPerWeek">
          <option :value="5">5</option>
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="40">40+</option>
        </select>
      </label>
      <label v-else class="auth-field">
        <span class="auth-field__label">Industry</span>
        <select v-model="form.industries[0]">
          <option>Share Market</option>
          <option>IT</option>
          <option>Medical</option>
          <option>Space</option>
          <option>Social Media</option>
          <option>Gaming</option>
          <option>Astrology</option>
        </select>
      </label>
      <p class="auth-field__hint">We store a band, not an exact amount, so ideas can respect your time and money.</p>
      <button type="button" class="btn btn--ghost" @click="showMore = !showMore">Improve my ideas</button>
      <div v-if="showMore" class="auth-form">
        <label class="auth-field">
          <span class="auth-field__label">City</span>
          <input v-model="form.city" type="text">
        </label>
        <p class="auth-field__hint">City changes which local schemes and customers we suggest.</p>
        <label class="auth-field">
          <span class="auth-field__label">State</span>
          <input v-model="form.state" type="text">
        </label>
      </div>
      <p v-if="error" class="form-error">{{ error }}</p>
      <button class="btn btn--primary btn--block" type="submit" :disabled="saving">{{ step === steps.length - 1 ? 'Save' : 'Next' }}</button>
    </form>
  </div>
</template>
