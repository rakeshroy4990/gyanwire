<script setup>
const props = defineProps({
  modelValue: { type: [Number, String, null], default: null },
  payback: { type: Object, default: null },
});

const emit = defineEmits(['update:modelValue']);

const OPTIONS = [
  { value: '', label: 'Off — don’t use payback' },
  { value: '100', label: '₹100 / hour' },
  { value: '200', label: '₹200 / hour' },
  { value: '350', label: '₹350 / hour' },
  { value: '500', label: '₹500 / hour' },
  { value: '750', label: '₹750 / hour' },
  { value: '1000', label: '₹1,000 / hour' },
  { value: '1500', label: '₹1,500 / hour' },
  { value: '2000', label: '₹2,000 / hour' },
];

function selectValue() {
  if (props.modelValue == null || props.modelValue === '') return '';
  return String(props.modelValue);
}

function onChange(event) {
  const raw = event.target.value;
  emit('update:modelValue', raw === '' ? null : raw);
}

function paybackLabel(payback) {
  if (!payback) return '';
  if (payback.state === 'pays_back') return 'Pays back over the plan (estimate)';
  if (payback.state === 'does_not_pay_back') return "Doesn't pay back over the plan (estimate)";
  return 'Payback is unclear at this value of time (estimate)';
}
</script>

<template>
  <div class="plan-vot">
    <label class="plan-vot__label" for="plan-vot-select">Value of your time (optional)</label>
    <div class="plan-vot__control">
      <select
        id="plan-vot-select"
        :value="selectValue()"
        @change="onChange"
      >
        <option
          v-for="opt in OPTIONS"
          :key="opt.value || 'off'"
          :value="opt.value"
        >
          {{ opt.label }}
        </option>
      </select>
    </div>
    <p class="plan-vot__hint">
      Used only to compare tool cost with hours you might save. Leave off if you prefer.
    </p>
    <p v-if="payback" class="plan-vot__hint">{{ paybackLabel(payback) }}</p>
  </div>
</template>
