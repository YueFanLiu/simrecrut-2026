import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import { createServer } from 'vite'
import { uiMessage } from '../src/utils/ui-message.js'

const localizedMessage = '\u8bf7\u91cd\u65b0\u767b\u5f55'

test('interface messages replace Chinese diagnostics and retain useful English details', () => {
  assert.equal(uiMessage(localizedMessage, 'Please sign in again.'), 'Please sign in again.')
  assert.equal(uiMessage('Saved. ' + localizedMessage, 'Please retry.'), 'Please retry.')
  assert.equal(uiMessage({ message: localizedMessage }, 'Please retry.'), 'Please retry.')
  assert.equal(uiMessage('', 'Please retry.'), 'Please retry.')
  assert.equal(uiMessage('This CV has expired.'), 'This CV has expired.')
})

test('connected requests normalize business and HTTP errors without changing response data', async () => {
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const { pageRequest } = await server.ssrLoadModule('/src/api/page-request.js')
    request.defaults.adapter = async config => ({ config, status: 200,
      data: { code: 500, msg: localizedMessage } })
    await assert.rejects(pageRequest('/test'), /service is unavailable/i)
    const response = { status: 403, data: { message: localizedMessage } }
    request.defaults.adapter = async config => {
      const error = new Error(localizedMessage)
      Object.assign(error, { config, response })
      throw error
    }
    await assert.rejects(pageRequest('/test'), /do not have permission/)
    await assert.rejects(request({ url: '/test', silent: true }), error => {
      assert.match(error.message, /do not have permission/)
      assert.equal(error.response, response)
      return true
    })
    request.defaults.adapter = async config => ({ config, status: 200,
      data: { code: 200, data: { filename: localizedMessage + '.pdf' } } })
    assert.equal((await pageRequest('/test')).filename, localizedMessage + '.pdf')
  } finally { await server.close() }
})

test('standalone resume workspace handles localized fetch and service errors in English', async () => {
  const root = new URL('../public/resume-workspace/', import.meta.url)
  const context = vm.createContext({
    document: { querySelector: () => ({ content: 'test-token' }) },
    fetch: async () => ({ ok: false, json: async () => ({ code: 500, message: localizedMessage }) }),
  })
  vm.runInContext(await readFile(new URL('core/dom.js', root), 'utf8'), context)
  vm.runInContext(await readFile(new URL('services/client.js', root), 'utf8'), context)
  await assert.rejects(vm.runInContext('api("/test")', context), /request could not be completed/)
  context.fetch = async () => { throw new Error(localizedMessage) }
  await assert.rejects(vm.runInContext('api("/test")', context), /service is unavailable/)
})
