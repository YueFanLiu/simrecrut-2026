const authPaths = new Set(['/login', '/register', '/forgot-password'])
const base = 'https://simrecrut.invalid'

// A return target is a local route, never an external URL or another auth page.
// Validate the normalized path too: dot segments and encoded separators can
// otherwise turn a seemingly local target into an auth loop or network URL.
export function safeAuthReturnPath(value) {
  if (typeof value !== 'string' || value.length > 2048 || !/^\/(?!\/)/.test(value)
    || /[\\\u0000-\u0020\u007f]/.test(value)) return ''
  try {
    const url = new URL(value, base)
    const decoded = decodeURIComponent(url.pathname)
    if (url.origin !== base || !/^\/(?!\/)/.test(url.pathname) || !/^\/(?!\/)/.test(decoded)
      || /[\\\u0000-\u0020\u007f]/.test(decoded) || /%(?:2f|5c|0[0-9a-f]|1[0-9a-f]|7f)/i.test(decoded)
      || authPaths.has(decoded.toLowerCase().replace(/\/+$/, ''))) return ''
    return url.pathname + url.search + url.hash
  } catch { return '' }
}

export function authPageRoute(name, returnTo) {
  const redirect = safeAuthReturnPath(returnTo)
  return { name, query: redirect ? { redirect } : {} }
}

export function resolvedAuthReturnPath(value, resolve) {
  const path = safeAuthReturnPath(value)
  if (!path) return ''
  try {
    const route = resolve(path)
    return route.matched?.length && !['NotFound', 'NotFoundFallback', 'Login', 'Register', 'ForgotPassword'].includes(route.name)
      ? route.fullPath : ''
  } catch { return '' }
}

export function afterSignIn(value, auth, demoMode, resolve) {
  const path = resolvedAuthReturnPath(value, resolve)
  if (path) return path
  const name = !demoMode && auth.hasDemoRole('Admin') ? 'AdminUsers'
    : !demoMode && auth.hasDemoRole('Candidate') ? 'ConnectedResumes' : 'DemoDashboard'
  return { name }
}

export function authGuardDestination(to, auth, demoMode, resolve) {
  if (to.meta.requiresDemoSession && !auth.isDemoSignedIn) return authPageRoute('Login', to.fullPath)
  if (to.meta.demoRole && !auth.hasDemoRole(to.meta.demoRole)) return { name: 'Forbidden' }
  if (to.meta.demoRoles && !to.meta.demoRoles.some(role => auth.hasDemoRole(role))) return { name: 'Forbidden' }
  if (!demoMode && to.meta.demoOnly) return { name: 'AssignPrimaryReviews' }
  if (['Login', 'Register'].includes(to.name) && auth.isDemoSignedIn) {
    return afterSignIn(to.query.redirect, auth, demoMode, resolve)
  }
}

export function guestBrowseDestination(value) {
  const path = safeAuthReturnPath(value)
  return path && /^\/candidate\/jobs(?:\/[^/]+)?\/?$/.test(new URL(path, base).pathname)
    ? path : { name: 'PublishedJobs' }
}

export function expiredSessionLoginUrl(location) {
  const returnTo = authPaths.has(location.pathname)
    ? new URLSearchParams(location.search || '').get('redirect')
    : `${location.pathname || ''}${location.search || ''}${location.hash || ''}`
  const path = safeAuthReturnPath(returnTo)
  return path ? `/login?redirect=${encodeURIComponent(path)}` : '/login'
}
