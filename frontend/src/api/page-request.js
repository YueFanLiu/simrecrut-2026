// Keep unavailable business APIs explicit; never fall back to synthetic records.
import request from '../utils/request.js'
import { getToken } from '../utils/auth.js'
import { requestErrorMessage } from '../utils/ui-message.js'

export async function pageRequest(url, method = 'get', data, envelope = false) {
  const sessionToken = getToken()
  try {
    const response = await request({ url, method, data, silent: true,
      headers: method === 'get' ? {} : { 'Idempotency-Key': crypto.randomUUID() } })
    if (getToken() !== sessionToken) throw new Error('Your session changed. Sign in and reload this page.')
    return envelope ? response : response.data ?? response.rows ?? response
  } catch (error) {
    if (error.response?.status === 404 || /^No static resource\s/i.test(error.message || '')) {
      throw new Error(`The backend has not implemented this page's API: ${url}`)
    }
    throw new Error(requestErrorMessage(error))
  }
}
export const resourceId = value => encodeURIComponent(String(value))
