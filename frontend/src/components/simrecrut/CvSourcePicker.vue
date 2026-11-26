<script setup>
import { reusableCv } from '../../utils/cv-workflow.js'
defineProps({ resumes: { type: Array, default: () => [] }, loading: Boolean, busy: Boolean,
  error: String, selectionError: String, selectedId: String })
const emit = defineEmits(['select', 'use', 'upload', 'review', 'refresh'])
const label = row => row.display_name || row.filename
</script>

<template>
  <section class="source-picker" :aria-busy="loading || busy" :aria-label="$t('Choose your CV')">
    <div class="heading"><div><h2>{{ $t('Choose your CV') }}</h2><p>{{ $t('Reuse a confirmed CV without uploading or extracting the PDF again.') }}</p></div><el-button :disabled="loading || busy" @click="emit('refresh')">{{ $t('Refresh list') }}</el-button></div>
    <p v-if="loading" class="status" role="status">{{ $t('Loading your CVs...') }}</p>
    <el-alert v-if="error" :title="$t(error)" type="error" :closable="false" show-icon />
    <p v-else-if="!loading && !resumes.length" class="empty">{{ $t('No saved CVs are available yet. You can upload a new PDF below.') }}</p>
    <fieldset v-if="resumes.length" :disabled="busy || loading">
      <legend class="sr-only">{{ $t('Your CV library') }}</legend>
      <article v-for="row in resumes" :key="row.id" class="cv-option" :class="{ selected: row.id === selectedId }">
        <label v-if="reusableCv(row)" class="cv-choice"><input type="radio" name="library-cv" :value="row.id" :checked="selectedId === row.id" @change="emit('select', row.id)"><span><strong>{{ label(row) }}</strong><small>{{ row.filename }}</small></span></label>
        <div v-else class="draft-name"><strong>{{ label(row) }}</strong><small>{{ $t(row.status === 'EXTRACTING' ? 'Extracting facts' : 'Review this CV before using it') }}</small></div>
        <el-tag v-if="reusableCv(row)" type="success" size="small">{{ $t('Ready to use') }}</el-tag>
        <el-button v-else size="small" :disabled="busy" @click="emit('review', row.id)">{{ $t('Review CV') }}</el-button>
      </article>
    </fieldset>
    <p v-if="selectionError" class="selection-error" role="alert">{{ $t(selectionError) }}</p>
    <el-button v-if="resumes.some(row => reusableCv(row))" type="primary" :disabled="loading || busy || !resumes.some(row => row.id === selectedId && reusableCv(row))" :loading="busy" @click="emit('use')">{{ $t('Use selected CV') }}</el-button>
    <div class="new-upload"><div><h3>{{ $t('Or upload a new CV') }}</h3><p>{{ $t('A new PDF uses the existing extraction service for this simulation.') }}</p><p class="scope-note">{{ $t('A new simulation upload is not automatically added to your CV library yet.') }}</p></div><el-button :disabled="busy || loading" @click="emit('upload')">{{ $t('Upload a new CV') }}</el-button></div>
  </section>
</template>

<style scoped>
.source-picker { padding:26px; background:var(--sr-surface); border:1px solid var(--sr-border); border-radius:12px; }
.heading,.new-upload { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:14px; }
h2,h3,p { margin:0; } h2 { color:var(--sr-ink); font-size:21px; } h3 { font-size:16px; color:var(--sr-text); }
.heading p,.new-upload p,.empty,.status { color:var(--sr-text-muted); font-size:13px; line-height:1.8; margin-top:7px; }
.heading { margin-bottom:18px; } .heading > div { flex:1; min-width:200px; }
fieldset { border:0; margin:0 0 18px; padding:0; display:flex; flex-direction:column; gap:10px; min-width:0; }
.cv-option { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:15px; border:1px solid var(--sr-border); border-radius:8px; }
.cv-option.selected { border-color:var(--sr-primary); background:var(--sr-primary-50); }
.cv-choice { display:flex; align-items:center; gap:12px; flex:1; min-width:0; cursor:pointer; }
input { accent-color:var(--sr-primary); flex:none; width:17px; height:17px; }
strong { display:block; color:var(--sr-text); font-size:14px; font-weight:600; overflow-wrap:anywhere; }
small { display:block; color:var(--sr-text-muted); font-size:12px; margin-top:5px; overflow-wrap:anywhere; }
.draft-name { flex:1; min-width:0; } .new-upload { padding-top:22px; margin-top:24px; border-top:1px solid var(--sr-border); }
.new-upload > div { flex:1; min-width:200px; } .scope-note { font-size:12px !important; }
.selection-error { color:var(--el-color-danger); margin:12px 0; font-size:13px; }
.empty { margin:20px 0; } .sr-only { position:absolute; width:1px; height:1px; overflow:hidden; clip-path:inset(50%); }
@media(max-width:600px) { .source-picker { padding:18px; } .cv-option { flex-wrap:wrap; } }
</style>
