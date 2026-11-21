<template>
  <div class="brand-mark">
    <!-- SimRecrut logo: a candidate inside a simulation loop, in ISEP blue, orange and yellow. -->
    <svg class="brand-logo" viewBox="0 0 48 48" width="42" height="42" aria-hidden="true">
      <rect width="48" height="48" rx="12" style="fill: var(--isep-blue, #1f4fa8)" />
      <path d="M38.5 17.5A16 16 0 1 0 39.4 30" fill="none" style="stroke: var(--isep-orange, #f39200)" stroke-width="4" stroke-linecap="round" />
      <circle cx="38.6" cy="17.6" r="3.3" style="fill: var(--isep-yellow, #fdc300)" />
      <circle cx="24" cy="19" r="5.2" fill="#fff" />
      <path d="M14.5 35.5a9.5 9.5 0 0 1 19 0z" fill="#fff" />
    </svg>
    <span class="brand-name"><span class="brand-sim">Sim</span><span class="brand-recrut">Recrut</span></span>
  </div>
</template>

<style scoped>
.brand-mark { display: flex; align-items: center; gap: 12px; color: var(--sr-ink); }
.brand-logo { flex: none; filter: drop-shadow(0 2px 4px rgb(14 33 71 / 18%)); }
.brand-name { font-size: 34px; font-weight: 750; letter-spacing: -1.2px; line-height: 1; white-space: nowrap; }
.brand-sim { color: var(--sr-ink); }
.brand-recrut { color: var(--sr-primary); }
</style>
