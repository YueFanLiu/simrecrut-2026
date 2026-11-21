<script setup>
import { language, languageOptions, setLanguage } from '../i18n/index.js'
</script>

<template>
  <div class="language-bar">
    <span class="isep-badge" aria-label="ISEP">
      <span class="isep-dot" aria-hidden="true"></span>ISEP
    </span>
    <label class="language-switcher">
      <span aria-hidden="true">◎</span>
      <span class="sr-only">{{ $t('Interface language') }}</span>
      <select :value="language" :aria-label="$t('Interface language')" @change="setLanguage($event.target.value)">
        <option v-for="option in languageOptions" :key="option.value" :value="option.value" :lang="option.lang">{{ option.label }}</option>
      </select>
    </label>
  </div>
  <div class="sr-header-accent" aria-hidden="true"></div>
</template>

<style scoped>
.language-bar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 6px clamp(16px, 3vw, 32px); background: var(--isep-navy); color: #fff; }
.isep-badge { display: inline-flex; align-items: center; gap: 7px; font-size: 12px; font-weight: 750; letter-spacing: 2px; }
.isep-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--isep-orange); box-shadow: 0 0 0 3px rgb(253 195 0 / 28%); }
.language-switcher { display: inline-flex; align-items: center; gap: 8px; color: #dbe4f5; }
select { max-width: 150px; border: 1px solid rgb(255 255 255 / 28%); border-radius: 6px; background: rgb(255 255 255 / 8%); color: #fff; font: inherit; font-size: 13px; padding: 4px 8px; cursor: pointer; }
select option { color: var(--sr-ink); background: #fff; }
select:hover { border-color: rgb(255 255 255 / 50%); }
select:focus-visible { outline: 2px solid var(--isep-yellow); outline-offset: 2px; }
.sr-header-accent { height: 3px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
</style>
