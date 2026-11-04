// Keep unavailable business APIs explicit; never fall back to synthetic records.
import request from '../utils/request.js'
import { getToken } from '../utils/auth.js'
import { requestErrorMessage } from '../utils/ui-message.js'

export async function pageRequest(url, method = 'get', data, envelope = false, operationKey) {
  const sessionToken = getToken()
  try {
    const response = await request({ url, method, data, silent: true,
      headers: method === 'get' ? {} : { 'Idempotency-Key': operationKey ?? crypto.randomUUID() } })
    if (getToken() !== sessionToken) throw new Error('Your session changed. Sign in and reload this page.')
    return envelope ? response : response.data ?? response.rows ?? response
  } catch (error) {
    if (/^No static resource\s/i.test(error.message || '') || (error.response?.status === 404 && !error.response.data?.message)) {
      throw new Error(`The backend has not implemented this page's API: ${url}`)
    }
    throw Object.assign(new Error(requestErrorMessage(error)), { status: error.response?.status })
  }
}
export const resourceId = value => encodeURIComponent(String(value))

// Existing list pages filter and paginate locally. Read each server page first.
export async function pageItems(url, filters = {}) {
  const items = []
  let page = 1
  let result
  do {
    const query = new URLSearchParams({ ...filters, page: page++, pageSize: 100 })
    result = await pageRequest(`${url}?${query}`)
    items.push(...result.items)
  } while (result.items.length && items.length < result.total)
  return items
}

export function utcTimestamp(value) {
  if (!value || /(?:Z|[+-]\d{2}:\d{2})$/.test(value)) return value
  return `${value}Z`
}
