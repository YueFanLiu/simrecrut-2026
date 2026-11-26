// Route resume operations through the authenticated Java transport.
import request from "@/utils/request";
// Proposed contract; gated by VITE_APP_CV_REVISIONS_ENABLED in the editor.
// Never fall back to PDF import or mutate the confirmed source on failure.
export const createResumeRevision = (sourceId, data, operationKey) => request({
  url: `/api/v1/resumes/${encodeURIComponent(sourceId)}/revisions`, method: 'post', data,
  headers: { 'Idempotency-Key': operationKey }, timeout: 15000, silent: true,
});
export const deleteResume = (id, operationKey = crypto.randomUUID()) => request({ url: "/api/v1/resumes/" + encodeURIComponent(id), method: "delete", headers: { "Idempotency-Key": operationKey }, timeout: 60000, silent: true });
export const listResumes = (signal) => request({ url: "/api/v1/resumes", signal, silent: true });
export const getResume = (id, signal) =>
  request({ url: "/api/v1/resumes/" + encodeURIComponent(id), signal, silent: true });
export const resumeAction = (id, action, data = {}, options = {}) =>
  request({
    url: "/api/v1/resumes/" + encodeURIComponent(id) + "/" + action,
    method: "post",
    headers: { "Idempotency-Key": options.operationKey || crypto.randomUUID() },
    signal: options.signal,
    timeout: action === "upload" ? 60000 : 15000,
    data,
    silent: options.silent ?? false,
  });
export const importResume = (data, signal, operationKey = crypto.randomUUID()) =>
  request({
    url: "/api/v1/resumes/import",
    method: "post",
    headers: { "Idempotency-Key": operationKey },
    data,
    timeout: 60000,
    signal,
    silent: true,
  });
