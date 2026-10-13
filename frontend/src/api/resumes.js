// Route resume operations through the authenticated RuoYi transport.
import request from "@/utils/request";
export const deleteResume = (id) => request({ url: "/api/v1/resumes/" + encodeURIComponent(id), method: "delete", headers: { "Idempotency-Key": crypto.randomUUID() }, timeout: 60000 });
export const listResumes = (signal) => request({ url: "/api/v1/resumes", signal });
export const getResume = (id, signal) =>
  request({ url: "/api/v1/resumes/" + encodeURIComponent(id), signal });
export const resumeAction = (id, action, data = {}, options = {}) =>
  request({
    url: "/api/v1/resumes/" + encodeURIComponent(id) + "/" + action,
    method: "post",
    headers: { "Idempotency-Key": options.operationKey || crypto.randomUUID() },
    signal: options.signal,
    timeout: action === "upload" ? 60000 : 15000,
    data,
  });
export const importResume = (data, signal) =>
  request({
    url: "/api/v1/resumes/import",
    method: "post",
    headers: { "Idempotency-Key": crypto.randomUUID() },
    data,
    timeout: 60000,
    signal,
  });
