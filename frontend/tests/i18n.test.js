import assert from 'node:assert/strict'
import test from 'node:test'
import { readdir, readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createSSRApp, effectScope, nextTick, ref } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createServer } from 'vite'
import messages from '../src/i18n/messages.json' with { type: 'json' }
import { dateLocale, elementLocale, language, readLanguage, setLanguage, storageKey, t, translateFor, useLocalizedValidation } from '../src/i18n/index.js'
import { validateIdentifier, validatePassword } from '../src/utils/login-validation.js'
import { validatePasswordConfirmation } from '../src/utils/registration-validation.js'

const locales = ['en', 'zh', 'fr']

test('supported languages persist safely and invalid or inaccessible storage falls back to English', () => {
  for (const locale of locales) assert.equal(readLanguage({ getItem: key => key === storageKey ? locale : null }), locale)
  assert.equal(readLanguage({ getItem: () => 'de' }), 'en')
  assert.equal(readLanguage({ getItem() { throw new Error('blocked') } }), 'en')
  assert.equal(readLanguage(undefined), 'en')
  setLanguage('fr')
  assert.equal(setLanguage('unsupported'), false)
  assert.equal(language.value, 'fr')
  const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
  try {
    Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: { setItem() { throw new Error('quota') } } })
    assert.equal(setLanguage('zh'), true)
    assert.equal(t('Sign in'), '\u767b\u5f55')
  } finally {
    if (original) Object.defineProperty(globalThis, 'localStorage', original)
    else delete globalThis.localStorage
    setLanguage('en')
  }
})

test('all UI catalogue entries have both translations; technical and user text stays intact', () => {
  assert.ok(Object.keys(messages).length > 1500)
  for (const [key, translations] of Object.entries(messages)) {
    for (const locale of ['zh', 'fr']) assert.ok(translations[locale]?.trim(), `${locale}: ${key}`)
    assert.equal(translateFor(key, 'en'), key)
  }
  for (const locale of locales) {
    for (const value of ['Wang Yinjie', 'Java, Vue et SQL', '\u6211\u7684\u7b80\u5386.pdf', 'd2f3864', 'SimRecrut']) assert.equal(translateFor(value, locale), value)
    assert.equal(translateFor(0, locale), 0)
    assert.equal(translateFor(null, locale), null)
  }
})

test('named messages retain parameter values and support legacy validation messages', () => {
  assert.equal(translateFor('Resend in {seconds}s', 'zh', { seconds: 42 }), '42 \u79d2\u540e\u91cd\u53d1')
  assert.equal(translateFor('Resend in {seconds}s', 'fr', { seconds: 42 }), 'Renvoyer dans 42 s')
  assert.equal(translateFor('Welcome, {name}', 'fr', { name: '<Wang>{seconds}' }), 'Bienvenue, <Wang>{seconds}')
  assert.equal(translateFor('Resolve language level before confirming.', 'zh'), '\u786e\u8ba4\u524d\u8bf7\u89e3\u51b3\u8bed\u8a00\u7b49\u7ea7\u95ee\u9898。')
  assert.equal(translateFor('Relevant Months must be a whole number between 0 and 600.', 'fr'), 'Le champ Mois pertinents doit être un entier entre 0 et 600.')
  assert.equal(translateFor('PENDING', 'zh'), '\u5f85\u5904\u7406')
  assert.equal(translateFor(translateFor('Please enter your password.', 'zh'), 'fr'), 'Veuillez saisir votre mot de passe.')
})

test('component and date locales follow the same language state', () => {
  const expected = { en: ['en', 'en-GB'], zh: ['zh-cn', 'zh-CN'], fr: ['fr', 'fr-FR'] }
  for (const locale of locales) {
    setLanguage(locale)
    assert.equal(elementLocale.value.name, expected[locale][0])
    assert.equal(dateLocale.value, expected[locale][1])
  }
  setLanguage('en')
})

test('login and registration validators use the selected language', () => {
  try {
    setLanguage('zh')
    validateIdentifier(null, '', error => assert.equal(error.message, '\u8bf7\u8f93\u5165\u7528\u6237\u540d\u6216\u90ae\u7bb1。'))
    validatePassword(null, '', error => assert.equal(error.message, '\u8bf7\u8f93\u5165\u5bc6\u7801。'))
    setLanguage('fr')
    assert.equal(validatePasswordConfirmation('one', 'two'), 'Les mots de passe ne correspondent pas.')
    validatePassword(null, '', error => assert.equal(error.message, 'Veuillez saisir votre mot de passe.'))
  } finally { setLanguage('en') }
})

test('language switching refreshes shown validation errors without touching inputs or untouched fields', async () => {
  const calls = []
  const input = ref('unsubmitted value')
  const form = ref({ fields: [{ prop: 'password', validateState: 'error' }, { prop: 'email', validateState: '' }], validateField: async prop => calls.push([prop, language.value]) })
  const scope = effectScope()
  scope.run(() => useLocalizedValidation(form))
  try {
    setLanguage('fr')
    await nextTick(); await nextTick()
    assert.deepEqual(calls, [['password', 'fr']])
    assert.equal(input.value, 'unsubmitted value')
  } finally { scope.stop(); setLanguage('en') }
})

test('template translation keys are covered and translated native options retain canonical values', async () => {
  const preserved = new Set(['SimRecrut', 'TP / TN / FP / FN', 'Top-K', 'UTC', 'YW', 'YYYY-MM-DD'])
  async function inspect(directory) {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const url = new URL(entry.name + (entry.isDirectory() ? '/' : ''), directory)
      if (entry.isDirectory()) { await inspect(url); continue }
      if (!entry.name.endsWith('.vue')) continue
      const source = (await readFile(url, 'utf8')).replaceAll('&quot;', '"')
      for (const match of source.matchAll(/\$t\(\s*('(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")/g)) {
        const key = match[1].slice(1, -1).replaceAll("\\'", "'").replaceAll('\\"', '"')
        assert.ok(messages[key.trim().replace(/\s+/g, ' ')] || preserved.has(key) || !/[A-Za-z]{2}/.test(key), `${entry.name}: ${key}`)
      }
      for (const option of source.matchAll(/<option\b([^>]*)>([\s\S]*?)<\/option>/g)) {
        if (option[2].includes('$t(')) assert.match(option[1], /(?:^|\s):?value=/, `${entry.name}: translated option needs a stable value`)
      }
    }
  }
  await inspect(new URL('../src/', import.meta.url))
})

test('workspace steps and result cards render in all three languages', async () => {
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)), server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const i18n = await server.ssrLoadModule('/src/i18n/index.js')
    const components = await Promise.all(['/src/components/simrecrut/CaseProgressStepper.vue', '/src/components/simrecrut/ResultCard.vue'].map(async path => (await server.ssrLoadModule(path)).default))
    for (const locale of locales) {
      i18n.setLanguage(locale)
      const app = createSSRApp(components[0])
      app.config.globalProperties.$t = i18n.t
      const html = await renderToString(app)
      assert.ok(html.includes(i18n.t('Upload CV')))
      assert.ok(html.includes(i18n.t('Review Facts')))
      const results = createSSRApp(components[1], { title: 'Neutral ML', status: 'Complete', decision: 'Accept', score: 0.9 })
      results.config.globalProperties.$t = i18n.t
      const resultHtml = await renderToString(results)
      assert.ok(resultHtml.includes(i18n.t('Accept')))
      assert.ok(resultHtml.includes('0.9'))
    }
  } finally { await server.close() }
})
