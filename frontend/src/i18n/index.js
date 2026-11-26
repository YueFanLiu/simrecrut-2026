import { computed, nextTick, readonly, ref, watch } from 'vue'
import en from 'element-plus/es/locale/lang/en.mjs'
import zh from 'element-plus/es/locale/lang/zh-cn.mjs'
import fr from 'element-plus/es/locale/lang/fr.mjs'
import messages from './messages.json' with { type: 'json' }

export const languageOptions = Object.freeze([
  { value: 'zh', label: '\u4e2d\u6587', lang: 'zh-CN' },
  { value: 'en', label: 'English', lang: 'en' },
  { value: 'fr', label: 'Français', lang: 'fr' },
])
const caseInsensitiveMessages = new Map(Object.entries(messages).map(([key, value]) => [key.toLowerCase(), value]))
const sourceMessages = new Map()
for (const [key, translations] of Object.entries(messages)) {
  for (const text of Object.values(translations)) {
    if (!text.includes('{') && !sourceMessages.has(text)) sourceMessages.set(text, key)
  }
}
// Legacy validators retain English messages in state. Match only the small set
// of parameterized UI messages so their displayed errors can switch languages.
const messagePatterns = Object.entries(messages).filter(([key]) => key.includes('{')).map(([key, translations]) => {
  const names = []
  const source = key.split(/(\{\w+\})/g).map(part => {
    if (/^\{\w+\}$/.test(part)) { names.push(part.slice(1, -1)); return '(.+?)' }
    return part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  }).join('')
  return { regex: new RegExp(`^${source}$`), names, translations }
})

export const storageKey = 'simrecrut.language'
export function readLanguage(storage) {
  try {
    const saved = storage?.getItem(storageKey)
    return languageOptions.some(option => option.value === saved) ? saved : 'en'
  } catch { return 'en' }
}
function browserStorage() {
  try { return globalThis.localStorage } catch { return undefined }
}
const selectedLanguage = ref(readLanguage(browserStorage()))
export const language = readonly(selectedLanguage)
export const elementLocale = computed(() => ({ en, zh, fr })[language.value])
export const dateLocale = computed(() => ({ en: 'en-GB', zh: 'zh-CN', fr: 'fr-FR' })[language.value])
export function setLanguage(value) {
  if (!languageOptions.some(option => option.value === value)) return false
  selectedLanguage.value = value
  try { browserStorage()?.setItem(storageKey, value) } catch { /* Session switching still works. */ }
  return true
}
export function translateFor(value, locale = 'en', params = {}) {
  if (typeof value !== 'string') return value
  const normalized = value.trim().replace(/\s+/g, ' ')
  const source = messages[normalized] ? normalized : sourceMessages.get(normalized) ?? normalized
  let translation = locale === 'en' ? (sourceMessages.has(normalized) ? source : value)
    : (messages[source] ?? caseInsensitiveMessages.get(source.toLowerCase()))?.[locale]
  if (translation === undefined && locale !== 'en') {
    for (const pattern of messagePatterns) {
      const match = normalized.match(pattern.regex)
      if (!match) continue
      translation = pattern.translations[locale]
      params = Object.fromEntries(pattern.names.map((name, index) => {
        const captured = match[index + 1]
        const label = ['field', 'group', 'category', 'item', 'label'].includes(name)
          ? (messages[captured] ?? caseInsensitiveMessages.get(captured.toLowerCase()))?.[locale] ?? captured
          : captured
        return [name, label]
      }))
      break
    }
  }
  translation ??= value
  return translation.replace(/\{(\w+)\}/g, (match, key) => Object.hasOwn(params, key) ? String(params[key]) : match)
}
export function t(value, params) { return translateFor(value, language.value, params) }
export function installI18n(app, router) {
  app.config.globalProperties.$t = t
  watch([language, () => router.currentRoute.value.meta.title], () => {
    document.documentElement.lang = languageOptions.find(option => option.value === language.value).lang
    document.title = `${t(router.currentRoute.value.meta.title || 'SimRecrut')} | SimRecrut`
  }, { immediate: true, flush: 'sync' })
}


// Revalidate only errors already shown; preserve values and untouched fields.
export function useLocalizedValidation(formRef) {
  watch(language, async () => {
    await nextTick()
    const form = formRef.value
    const fields = form?.fields?.filter(field => field.validateState === 'error') || []
    for (const field of fields) await form.validateField(field.prop).catch(() => {})
  })
}
