<script setup>
defineProps({ readyForReview: Boolean, currentStep: { type: Number, default: 0 }, reviewConfirmed: Boolean, simulationComplete: Boolean })
const steps = ['Upload CV', 'Review Facts', 'Run Simulation', 'View Results']
</script>

<template>
  <ol class="case-steps" :aria-label="$t(&quot;Simulation steps&quot;)">
    <li v-for="(step, index) in steps" :key="step" :class="{ current: index === currentStep, completed: index < currentStep || (index === 0 && readyForReview) || (index === 1 && reviewConfirmed) || (index === 2 && simulationComplete) }"
      :aria-current="index === currentStep ? 'step' : undefined">
      <span class="step-number" aria-hidden="true">{{ $t(index < currentStep || (index === 0 && readyForReview) || (index === 1 && reviewConfirmed) || (index === 2 && simulationComplete) ? '✓' : index + 1) }}</span>
      <span>{{ $t(step) }}</span>
    </li>
  </ol>
</template>

<style scoped>
.case-steps { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; padding: 0; margin: 28px 0; list-style: none; }
li { display: flex; align-items: center; gap: 10px; color: var(--sr-text-subtle); font-size: 13px; line-height: 1.6; }
.step-number { flex: 0 0 30px; height: 30px; display: grid; place-items: center; border-radius: 50%; color: var(--sr-text-muted); background: var(--sr-border); }
.current { color: var(--sr-primary); font-weight: 600; }
.current .step-number { background: var(--sr-primary); color: white; }
.completed .step-number { background: #218458; }
@media (max-width: 700px) { .case-steps { grid-template-columns: repeat(2, 1fr); gap: 14px; } }
</style>
