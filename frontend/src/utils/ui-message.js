import { language, t } from '../i18n/index.js'

// Translate system messages while retaining unknown diagnostics only in the selected language.
// User-authored data (names, CV facts and evidence) must remain unchanged.
export function uiMessage(value, fallback = 'The request could not be completed. Please try again.') {
  if (typeof value !== 'string' || !value.trim()) return t(fallback)
  const translated = t(value.trim())
  return language.value === 'zh' || !/\p{Script=Han}/u.test(translated) ? translated : t(fallback)
}

export function requestErrorMessage(error) {
  const status = error.response?.status
  const fallback = status === 401
    ? 'Your session has expired. Please sign in again.'
    : status === 403
      ? 'You do not have permission to perform this action.'
      : status === 404
        ? 'The requested service is not available yet.'
        : error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
          ? 'The request timed out. Please try again.'
          : 'The service is unavailable. Please try again.'
  const serverMessage = error.response?.data?.message || error.response?.data?.msg
  const transportFailure = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
    || Boolean(error.response)
    || /^(Network Error|Failed to fetch|Load failed|fetch failed)$/i.test(error.message || '')
  return uiMessage(serverMessage || (transportFailure ? undefined : error.message), fallback)
}
