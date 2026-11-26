import test from 'node:test'
import assert from 'node:assert/strict'
import { createMemoryHistory, createRouter } from 'vue-router'
import { safeAuthReturnPath, authPageRoute, resolvedAuthReturnPath, afterSignIn,
  authGuardDestination, guestBrowseDestination, expiredSessionLoginUrl } from '../src/utils/auth-navigation.js'

const view = { render: () => null }
function navigation() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/login', name: 'Login', component: view },
    { path: '/register', name: 'Register', component: view },
    { path: '/forgot-password', name: 'ForgotPassword', component: view },
    { path: '/candidate/jobs', name: 'PublishedJobs', component: view },
    { path: '/candidate/jobs/:jobId', name: 'JobDetail', component: view },
    { path: '/candidate/cases/:caseId/review', name: 'CvReview', component: view,
      meta: { requiresDemoSession: true, demoRole: 'Candidate' } },
    { path: '/candidate/resumes', name: 'ConnectedResumes', component: view,
      meta: { requiresDemoSession: true, demoRole: 'Candidate' } },
    { path: '/dashboard', name: 'DemoDashboard', component: view, meta: { requiresDemoSession: true } },
    { path: '/admin/users', name: 'AdminUsers', component: view, meta: { requiresDemoSession: true, demoRole: 'Admin' } },
    { path: '/403', name: 'Forbidden', component: view, meta: { requiresDemoSession: true } },
    { path: '/:pathMatch(.*)*', name: 'NotFoundFallback', component: view },
  ] })
  const auth = { isDemoSignedIn: false, role: '', hasDemoRole(role) { return this.role === role } }
  router.beforeEach(to => authGuardDestination(to, auth, false, path => router.resolve(path)))
  return { router, auth, resolve: path => router.resolve(path) }
}

test('local job and resource targets retain bounded context while external URLs and auth loops are rejected', () => {
  const target = '/candidate/jobs/JOB?resumeId=' + 'a'.repeat(32) + '#requirements'
  assert.equal(safeAuthReturnPath(target), target)
  for (const value of [undefined, null, [], [target], {}, '', 'https://outside.test', '//outside.test',
    '/\\outside.test', '/candidate/jobs/\\x', '/candidate/jobs/\n', '/login', '/LOGIN/', '/register?redirect=/login',
    '/forgot-password', '/candidate/../login', '/candidate/..//outside.test', '/%2foutside.test',
    '/candidate/jobs/%5cfoo', '/candidate/jobs/%255cfoo', '/candidate/jobs/%00', '/candidate/jobs/%',
    '/' + 'x'.repeat(2048)]) assert.equal(safeAuthReturnPath(value), '', String(value))
})

test('login, registration and recovery links carry the same target without carrying extra query fields', () => {
  const target = '/candidate/jobs/software-engineer?resumeId=' + 'b'.repeat(32)
  for (const name of ['Login', 'Register', 'ForgotPassword']) {
    assert.deepEqual(authPageRoute(name, target), { name, query: { redirect: target } })
    assert.deepEqual(authPageRoute(name, '//outside.test'), { name, query: {} })
  }
})

test('unknown routes fall back to the appropriate workspace; signed-in visits to auth pages use the same destination', async () => {
  const { router, auth, resolve } = navigation()
  auth.isDemoSignedIn = true; auth.role = 'Candidate'
  assert.equal(resolvedAuthReturnPath('/missing', resolve), '')
  assert.deepEqual(afterSignIn('/missing', auth, false, resolve), { name: 'ConnectedResumes' })
  assert.deepEqual(afterSignIn('', auth, true, resolve), { name: 'DemoDashboard' })
  auth.role = 'Admin'
  assert.deepEqual(afterSignIn('', auth, false, resolve), { name: 'AdminUsers' })
  auth.role = 'HR'
  assert.deepEqual(afterSignIn('', auth, false, resolve), { name: 'DemoDashboard' })
  await router.push(authPageRoute('Register', '/candidate/jobs/JOB'))
  assert.equal(router.currentRoute.value.fullPath, '/candidate/jobs/JOB')
})

test('a protected deep link survives registration and sign-in; role checks still run on return', async () => {
  const { router, auth, resolve } = navigation()
  const target = '/candidate/cases/CASE/review?resumeId=' + 'c'.repeat(32)
  await router.push(target)
  assert.equal(router.currentRoute.value.name, 'Login')
  assert.equal(router.currentRoute.value.query.redirect, target)
  await router.push(authPageRoute('Register', router.currentRoute.value.query.redirect))
  await router.push(authPageRoute('Login', router.currentRoute.value.query.redirect))
  auth.isDemoSignedIn = true; auth.role = 'Candidate'
  await router.replace(afterSignIn(router.currentRoute.value.query.redirect, auth, false, resolve))
  assert.equal(router.currentRoute.value.fullPath, target)
  await router.push('/candidate/jobs')
  auth.role = 'HR'
  await router.push(authPageRoute('Login', target))
  assert.equal(router.currentRoute.value.name, 'Forbidden')
})

test('guest browsing can return to a public job but never to a private CV or case', () => {
  assert.equal(guestBrowseDestination('/candidate/jobs/JOB?resumeId=' + 'd'.repeat(32)), '/candidate/jobs/JOB?resumeId=' + 'd'.repeat(32))
  for (const value of ['/candidate/resumes', '/candidate/cases/CASE/review', '/candidate/jobs/JOB/private',
    '/candidate/jobs-other', 'https://outside.test', '/login']) {
    assert.deepEqual(guestBrowseDestination(value), { name: 'PublishedJobs' })
  }
})

test('expired sessions retain the current internal page or the existing auth return target', () => {
  const target = '/candidate/cases/CASE/review?resumeId=' + 'e'.repeat(32) + '#facts'
  assert.equal(expiredSessionLoginUrl({ pathname: '/candidate/cases/CASE/review',
    search: '?resumeId=' + 'e'.repeat(32), hash: '#facts' }), '/login?redirect=' + encodeURIComponent(target))
  assert.equal(expiredSessionLoginUrl({ pathname: '/register', search: '?redirect=' + encodeURIComponent(target) }),
    '/login?redirect=' + encodeURIComponent(target))
  assert.equal(expiredSessionLoginUrl({ pathname: '/register', search: '?redirect=https://outside.test' }), '/login')
})
