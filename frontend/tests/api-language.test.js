import assert from 'node:assert/strict'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { createServer } from 'vite'
import messages from '../src/i18n/messages.json' with { type: 'json' }

async function connected(run) {
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const i18n = await server.ssrLoadModule('/src/i18n/index.js')
    const { pageRequest } = await server.ssrLoadModule('/src/api/page-request.js')
    const { uiMessage, requestErrorMessage } = await server.ssrLoadModule('/src/utils/ui-message.js')
    await run({ request, i18n, pageRequest, uiMessage, requestErrorMessage })
    i18n.setLanguage('en')
  } finally { await server.close() }
}

test('each authenticated and public request uses the current UI language without changing its body', async () => {
  await connected(async ({ request, i18n }) => {
    const received = []
    request.defaults.adapter = async config => {
      received.push({ language: config.headers.get('Accept-Language'), public: config.publicRequest, data: JSON.parse(config.data) })
      return { config, status: 200, data: { code: 0, data: { filename: '\u4e2d\u6587\u7b80\u5386.pdf', title: 'Poste français', reason: 'Access denied.' } } }
    }
    const body = { email: 'person@example.test', professional: { skills: ['Java', '\u4e2d\u6587'] } }
    for (const [value, header] of [['zh', 'zh-CN'], ['en', 'en'], ['fr', 'fr']]) {
      i18n.setLanguage(value)
      for (const isPublic of [false, true]) {
        const response = await request({ url: '/api/v1/test', method: 'post', data: body,
          silent: true, headers: isPublic ? { isToken: false } : {} })
        assert.deepEqual(received.at(-1), { language: header, public: isPublic, data: body })
        assert.deepEqual(response.data, { filename: '\u4e2d\u6587\u7b80\u5386.pdf', title: 'Poste français', reason: 'Access denied.' })
      }
    }
  })
})

test('backend Chinese and French errors remain localized and retain request IDs and machine codes', async () => {
  await connected(async ({ request, i18n, pageRequest, uiMessage, requestErrorMessage }) => {
    for (const [locale, message] of [['zh', '\u7528\u6237\u540d、\u90ae\u7bb1\u6216\u5bc6\u7801\u9519\u8bef。'], ['fr', 'Nom d’utilisateur, e-mail ou mot de passe incorrect.'], ['en', 'Invalid username/email or password.']]) {
      i18n.setLanguage(locale)
      const envelope = { code: 400, message, requestId: 'trace-123', data: { retryable: false, errorCode: 'INVALID_CREDENTIALS' } }
      request.defaults.adapter = async config => ({ config, status: 200, data: envelope })
      await assert.rejects(pageRequest('/api/v1/test'), error => {
        assert.equal(error.message, message)
        assert.equal(error.status, 400)
        assert.equal(error.response.data, envelope)
        assert.equal(error.response.data.requestId, 'trace-123')
        assert.equal(error.response.data.data.errorCode, 'INVALID_CREDENTIALS')
        return true
      })
    }
    i18n.setLanguage('zh')
    assert.equal(uiMessage('\u540e\u7aef\u8fd4\u56de\u7684\u4e2d\u6587\u63d0\u793a。'), '\u540e\u7aef\u8fd4\u56de\u7684\u4e2d\u6587\u63d0\u793a。')
    assert.equal(requestErrorMessage({ code: 'ETIMEDOUT' }), '\u8bf7\u6c42\u8d85\u65f6，\u8bf7\u91cd\u8bd5。')
    // A request may finish after the user chooses another language.
    i18n.setLanguage('fr')
    assert.equal(uiMessage('\u7528\u6237\u540d、\u90ae\u7bb1\u6216\u5bc6\u7801\u9519\u8bef。'), 'Nom d’utilisateur, e-mail ou mot de passe incorrect.')
  })
})

test('HTTP errors preserve retry metadata and translate transport failures in the selected language', async () => {
  await connected(async ({ request, i18n, pageRequest }) => {
    i18n.setLanguage('fr')
    const response = { status: 429, headers: { 'retry-after': '7', 'content-language': 'fr' },
      data: { code: 429, message: 'Trop de requêtes. Réessayez plus tard.', requestId: 'trace-429', data: { retryable: true } } }
    request.defaults.adapter = async config => { throw Object.assign(new Error('technical transport text'), { config, response }) }
    await assert.rejects(pageRequest('/api/v1/test'), error => {
      assert.equal(error.message, response.data.message)
      assert.equal(error.response, response)
      assert.equal(error.status, 429)
      assert.equal(error.response.headers['retry-after'], '7')
      return true
    })
    i18n.setLanguage('zh')
    request.defaults.adapter = async config => { throw Object.assign(new Error('Network Error'), { config, code: 'ECONNABORTED' }) }
    // Unknown low-level English text should yield the translated status-specific fallback.
    await assert.rejects(pageRequest('/api/v1/test'), error => {
      assert.equal(error.message, '\u8bf7\u6c42\u8d85\u65f6，\u8bf7\u91cd\u8bd5。')
      return true
    })
  })
})

test('frontend fallbacks and Java API bundles share translations', async () => {
  function properties(text) {
    return new Map(text.split(/\r?\n/).filter(line => line && !line.startsWith('#')).map(line => {
      const index = line.indexOf('=')
      return [line.slice(0, index), line.slice(index + 1).replaceAll('\\n', '\n')]
    }))
  }
  const directory = new URL('../../backend/java/src/main/resources/i18n/', import.meta.url)
  const english = properties(await readFile(new URL('messages.properties', directory), 'utf8'))
  for (const locale of ['zh', 'fr']) {
    const backend = properties(await readFile(new URL(`messages_${locale}.properties`, directory), 'utf8'))
    for (const [key, source] of english) {
      if (!key.startsWith('api.')) continue
      assert.equal(key, 'api.' + createHash('sha256').update(source).digest('hex').slice(0, 16))
      assert.equal(messages[source]?.[locale], backend.get(key), `${locale}: ${source}`)
    }
  }
})
