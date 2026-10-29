<script setup>
defineProps({ name: { type: String, required: true } })
</script>

<template>
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name === 'user'">
      <circle cx="12" cy="7" r="4" />
      <path d="M4 21v-2a8 8 0 0 1 16 0v2z" />
    </template>
    <template v-else-if="name === 'email'">
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="M3 6l9 7 9-7" />
    </template>
    <template v-else-if="name === 'lock'">
      <rect x="5" y="10" width="14" height="11" rx="2" />
      <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
    </template>
    <template v-else>
      <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
      <path v-if="name === 'eye-off'" d="M3 3l18 18" />
    </template>
  </svg>
</template>
