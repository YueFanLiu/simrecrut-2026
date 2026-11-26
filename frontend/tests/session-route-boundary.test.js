import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import * as Vue from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, onBeforeRouteLeave } from 'vue-router'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'
import { createServer } from 'vite'

// Compile the real App template for the lightweight renderer: Vite's SSR loader
// otherwise supplies ssrRender, which cannot exercise RouterView unmounting.
async function clientTemplate(path) {
  const { descriptor } = parse(await readFile(path, 'utf8'), { filename: path })
  const { bindings } = compileScript(descriptor, { id: 'session-test' })
  const { code, errors } = compileTemplate({ source: descriptor.template.content,
    filename: path, id: 'session-test', compilerOptions: { bindingMetadata: bindings } })
  assert.deepEqual(errors, [])
  const executable = code.replace(/import \{([^}]+)\} from "vue"/, (_, names) =>
    `const {${names.replace(/\bas\b/g, ':')}} = Vue`)
    .replace('export function render', 'return function render')
  return new Function('Vue', executable)(Vue)
}

const node = () => ({ children: [], parent: null })
function remove(child) {
  if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1)
  child.parent = null
}
const renderer = Vue.createRenderer({ createComment: node, createText: node, createElement: node,
  insert(child, parent, anchor = null) {
    remove(child); child.parent = parent
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, child)
  }, remove, parentNode: child => child.parent,
  nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] || null,
  setText() {}, setElementText() {}, patchProp() {} })

test('private routes unmount on sign-out before a dirty-page guard can cancel navigation', async () => {
  const server = await createServer({ mode: 'connected', root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  let app
  try {
    const { default: App } = await server.ssrLoadModule('/src/App.vue')
    const { default: switcher } = await server.ssrLoadModule('/src/components/LanguageSwitcher.vue')
    switcher.render = () => null
    App.render = await clientTemplate(fileURLToPath(new URL('../src/App.vue', import.meta.url)))
    const { useAuthStore } = await server.ssrLoadModule('/src/store/modules/auth.js')
    const pinia = createPinia(), auth = useAuthStore(pinia)
    const owner = username => ({ username, role: 'Candidate', roles: ['candidate'] })
    auth.demoUser = owner('first-user')
    let mounted = 0, unmounted = 0, guardCalls = 0
    const Private = { setup() {
      mounted++
      onBeforeRouteLeave(() => { guardCalls++; return false })
      Vue.onUnmounted(() => { unmounted++ })
      return () => null
    } }
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/review', component: Private, meta: { requiresDemoSession: true } },
      { path: '/login', name: 'Login', component: { render: () => null } },
    ] })
    await router.push('/review')
    app = renderer.createApp(App); app.use(pinia); app.use(router)
    app.provide(Vue.ssrContextKey, { modules: new Set() }); app.mount(node())
    await Vue.nextTick()
    assert.equal(mounted, 1)
    // Switching accounts also discards all component-local private state.
    auth.demoUser = owner('second-user'); await Vue.nextTick()
    assert.equal(unmounted, 1)
    assert.equal(mounted, 2)
    await auth.logout()
    await router.replace({ name: 'Login' })
    await Vue.nextTick()
    assert.equal(unmounted, 2)
    assert.equal(guardCalls, 0)
    assert.equal(router.currentRoute.value.name, 'Login')
  } finally { app?.unmount(); await server.close() }
})
