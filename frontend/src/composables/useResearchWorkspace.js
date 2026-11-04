import { demoMode } from '../config/runtime.js'
import { computed, inject, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store/modules/auth.js'
import { useResearchModuleStore } from '../store/modules/research-module.js'
import { researchExperimentsScenario } from '../config/demo.js'
import { researchFactors, researchJobs } from '../mock/research-module.js'
import { summarizeResearchResults } from '../mock/research-results.js'

export const researchWorkspaceKey = Symbol('researchWorkspace')
export function useWorkspace() {
  const workspace = inject(researchWorkspaceKey)
  if (!workspace) throw new Error('Research workspace provider is missing.')
  return workspace
}
export function createResearchWorkspace() {
  const route = useRoute(), router = useRouter(), auth = useAuthStore(), store = useResearchModuleStore()
  const clock = ref(Date.now()), loading = ref(true), loadError = ref(''), toast = ref(''), error = ref(''), drawer = ref(null)
  const search = ref(''), status = ref('All statuses'), job = ref('All jobs'), sort = ref('latest'), currentPage = ref(1), pageSize = ref(5)
  const source = ref('Reviewed Profiles'), tab = ref('Configuration'), selected = ref([]), targetExperiment = ref('')
  const factor = ref('Gender'), reviewer = ref('reviewer-hr'), condition = ref('Standard')
  const form = reactive({ name: '', type: '', job: '', weight: '', protocol: '', neutral: '', biased: '' })
  const ids = { ResearchExperiments: 'R01', ResearchExperimentCreate: 'R02', ResearchExperimentDetail: 'R03', ResearchSubjects: 'R04', ResearchModels: 'R05', ResearchMetrics: 'R06' }
  const page = computed(() => [ids[route.name] || 'R01'])
  const data = computed(() => ({
    experiments: auth.hasDemoRole('Researcher') && researchExperimentsScenario !== 'empty' ? store.listExperiments(auth.demoUser, clock.value) : [],
    subjects: auth.hasDemoRole('Researcher') ? store.listSubjects(auth.demoUser, clock.value) : [],
    models: auth.hasDemoRole('Researcher') ? store.availableModels.map(({ id, name, version, status, schema, protocol, mode, cutoff, evaluation }) => ({ id, name, version, status, schema, protocol, mode, cutoff, evaluation })) : [],
  }))
  const experiment = computed(() => data.value.experiments.find(row => row.id === (route.params.experimentId || route.query.experimentId)))
  const missingRecord = computed(() => ['R03', 'R06'].includes(page.value[0]) && !experiment.value)
  const resultSummary = computed(() => Object.fromEntries(['objective', 'neutral', 'biased'].map(method =>
    [method, summarizeResearchResults(experiment.value?.outputs || [], method)])))
  const list = computed(() => ({ R01: data.value.experiments, R04: data.value.subjects, R05: data.value.models })[page.value[0]] || [])
  const statuses = computed(() => [...new Set(list.value.map(row => row.status))])
  const jobs = computed(() => [...new Set(data.value.experiments.map(row => row.job))])
  const filtered = computed(() => list.value.filter(row => (page.value[0] !== 'R04' || row.source === source.value)
    && (status.value === 'All statuses' || row.status === status.value) && (page.value[0] !== 'R01' || job.value === 'All jobs' || row.job === job.value)
    && [row.id, row.name, row.job, row.type, row.protocol].some(value => String(value || '').toLowerCase().includes(search.value.trim().toLowerCase())))
    .sort((a, b) => (sort.value === 'oldest' ? 1 : -1) * String(a.date || '').localeCompare(String(b.date || '')) || a.id.localeCompare(b.id)))
  const rows = computed(() => filtered.value.slice((currentPage.value - 1) * pageSize.value, currentPage.value * pageSize.value))
  const drafts = computed(() => data.value.experiments.filter(row => row.status === 'Draft'))
  const pageResources = { R01: ['experiments'], R02: ['models', 'jobs'],
    R03: ['experiments', 'subjects'], R04: ['subjects', 'experiments'], R05: ['models'], R06: ['experiments'] }
  let active = true, request = 0, toastTimer
  const timer = setInterval(() => { clock.value = Date.now() }, 1000)
  function notify(message) { toast.value = message; clearTimeout(toastTimer); toastTimer = setTimeout(() => { toast.value = '' }, 5000) }
  async function act(action) { error.value = ''; try { await action(); if (!demoMode) await refresh(); clock.value = Date.now() } catch (failure) { error.value = failure.message } }
  function go(path) { router.push(path) }
  function reset() { search.value = ''; status.value = 'All statuses'; job.value = 'All jobs'; sort.value = 'latest'; currentPage.value = 1 }
  function select(id) { if (!data.value.subjects.some(row => row.id === id)) return; selected.value = selected.value.includes(id) ? selected.value.filter(value => value !== id) : [...selected.value, id] }
  function create() { act(async () => { const id = await store.createExperiment(form, auth.demoUser); Object.keys(form).forEach(key => { form[key] = '' }); go(`/research/experiments/${id}`); notify(demoMode ? 'Demo draft created. No execution was started.' : 'Draft created.') }) }
  function saveSubjects() { act(async () => { await store.saveSubjects(experiment.value.id, selected.value, auth.demoUser); notify(demoMode ? 'Demo subject selection saved.' : 'Subject selection saved.') }) }
  function addSubjects() { act(async () => { const row = store.readExperiment(targetExperiment.value, auth.demoUser); await store.saveSubjects(row.id, [...row.subjectIds, ...selected.value], auth.demoUser); go(`/research/experiments/${row.id}`); notify(demoMode ? 'Authorised demo subjects added.' : 'Authorized subjects added.') }) }
  function createVariants() { act(async () => { await store.createVariants(experiment.value.id, factor.value, auth.demoUser); notify(demoMode ? 'Demo single-factor pairs created with unchanged professional facts.' : 'Variant request completed.') }) }
  async function run() {
    error.value = ''
    try { await store.runExperiment(experiment.value.id, auth.demoUser); if (!demoMode) await refresh(); if (active && auth.hasDemoRole('Researcher')) notify(demoMode ? 'Synthetic demo finished. Open Comparison or Metrics to inspect illustrative outputs.' : 'Run request accepted. Refresh to check its status.') }
    catch (failure) { if (active) error.value = failure.message }
  }
  function assign() { act(async () => { await store.assignReviews(experiment.value.id, reviewer.value, condition.value, auth.demoUser); notify(demoMode ? 'Pending demo research reviews assigned. No review decisions were generated.' : 'Review assignment saved.') }) }
  function view(row) { drawer.value = { id: row.id, kind: page.value[0] === 'R04' ? 'subject' : 'model' } }
  function assignPrimary() { act(async () => { await store.assignPrimaryReview(drawer.value.id, reviewer.value, condition.value, auth.demoUser); notify(demoMode ? 'Pending Primary demo review assigned. No decision or notification was generated.' : 'Primary review assignment saved.') }) }
  const drawerRecord = computed(() => drawer.value ? (drawer.value.kind === 'subject' ? data.value.subjects : data.value.models).find(row => row.id === drawer.value.id) : null)
  watch(drawerRecord, value => { if (!value) drawer.value = null })
  function useModel(row) { if (row.status === 'Active') go(`/research/experiments/new?modelId=${encodeURIComponent(row.id)}`) }
  async function refresh() {
    const sequence = ++request; loading.value = true; loadError.value = ''
    if (!demoMode) {
      try {
        await store.loadResearch(pageResources[page.value[0]])
        if (!active || sequence !== request) return
        let id = route.params.experimentId || route.query.experimentId
        if (page.value[0] === 'R06' && !id && store.experiments.length) {
          await router.replace({ name: 'ResearchMetrics', query: { experimentId: store.experiments[0].id } })
          return
        }
        if (id) {
          const detail = await (await import('../api/research.js')).experimentDetail(id)
          if (active && sequence === request) store.experiments = [...store.experiments.filter(row => row.id !== id), detail]
        }
        selected.value = [...(experiment.value?.subjectIds || [])]
        factor.value = experiment.value?.factor || 'Gender'
        if (page.value[0] === 'R02') {
          const model = store.models.find(row => row.id === route.query.modelId && row.status === 'Active')
          if (model) { form[model.name === 'Neutral ML' ? 'neutral' : 'biased'] = `${model.id} / ${model.version}`; form.protocol = model.protocol }
        }
      } catch (failure) { if (active && sequence === request) loadError.value = failure.message }
      if (active && sequence === request) { clock.value = Date.now(); loading.value = false }
      return
    }
    await new Promise(resolve => setTimeout(resolve, 300))
    if (!active || sequence !== request || !auth.hasDemoRole('Researcher')) return
    loadError.value = researchExperimentsScenario === 'error' ? 'Unable to load Research demo data. Please try again.' : ''
    clock.value = Date.now(); loading.value = false
  }
  watch([search, status, job, sort, source, pageSize], () => { currentPage.value = 1 })
  watch(() => filtered.value.length, () => { currentPage.value = Math.min(currentPage.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value))) })
  watch(() => data.value.subjects.map(row => row.id).join(','), () => { selected.value = selected.value.filter(id => data.value.subjects.some(row => row.id === id)) })
  watch(() => route.fullPath, () => {
    if (!demoMode) refresh()
    reset(); tab.value = 'Configuration'; drawer.value = null; error.value = ''; selected.value = [...(experiment.value?.subjectIds || [])]; factor.value = experiment.value?.factor || 'Gender'
    if (page.value[0] === 'R02') {
      Object.keys(form).forEach(key => { form[key] = '' })
      const model = data.value.models.find(row => row.id === route.query.modelId && row.status === 'Active')
      if (model) { form[model.name === 'Neutral ML' ? 'neutral' : 'biased'] = `${model.id} / ${model.version}`; form.protocol = model.protocol }
    }
  }, { immediate: true })
  onBeforeUnmount(() => { active = false; request += 1; clearInterval(timer); clearTimeout(toastTimer); drawer.value = null })
  if (demoMode) refresh()
  return { page, data, experiment, missingRecord, search, status, statuses, job, jobs, sort, currentPage, pageSize, filtered, rows, source, tab, selected, targetExperiment, drafts,
    form, error, toast, loading, loadError, drawer, drawerRecord, factor, factors: researchFactors, reviewer, condition, researchJobs: computed(() => demoMode ? researchJobs : store.jobs), resultSummary,
    go, notify, reset, select, create, saveSubjects, addSubjects, createVariants, run, assign, assignPrimary, view, useModel, refresh,
    badge: value => String(value).toLowerCase().replaceAll(' ', '-') }
}
