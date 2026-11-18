<script setup>
import { language, languageOptions, setLanguage } from '../i18n/index.js'
</script>

<template>
  <div class="language-bar">
    <label class="language-switcher">
      <span aria-hidden="true">◎</span>
      <span class="sr-only">{{ $t('Interface language') }}</span>
      <select :value="language" :aria-label="$t('Interface language')" @change="setLanguage($event.target.value)">
        <option v-for="option in languageOptions" :key="option.value" :value="option.value" :lang="option.lang">{{ option.label }}</option>
      </select>
    </label>
  </div>
</template>

<style scoped>
.language-bar { display: flex; justify-content: flex-end; padding: 8px clamp(16px, 3vw, 32px); background: #fff; border-bottom: 1px solid #e1eaf5; }
.language-switcher { display: inline-flex; align-items: center; gap: 8px; color: #20354e; }
select { max-width: 150px; border: 1px solid #b6c9df; border-radius: 6px; background: #fff; color: inherit; font: inherit; font-size: 14px; padding: 5px 8px; cursor: pointer; }
select:focus-visible { outline: 2px solid #176bdf; outline-offset: 2px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
</style>
