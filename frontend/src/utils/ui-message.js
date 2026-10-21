// Keep service diagnostics out of interface copy when they contain Chinese text.
// User-authored data (names, CV facts and evidence) must remain unchanged.
export function uiMessage(value, fallback = 'The request could not be completed. Please try again.') {
  return typeof value === 'string' && value.trim() && !/\p{Script=Han}/u.test(value)
    ? value.trim()
    : fallback
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
  return uiMessage(error.response?.data?.message || error.response?.data?.msg || error.message, fallback)
}
