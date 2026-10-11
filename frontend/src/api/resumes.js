// Route resume operations through the authenticated RuoYi transport.
import request from "@/utils/request";
export const listResumes = () => request({ url: "/simrecrut/resumes" });
export const getResume = (id) =>
  request({ url: "/simrecrut/resumes/" + encodeURIComponent(id) });
export const resumeAction = (id, action, data = {}) =>
  request({
    url: "/simrecrut/resumes/" + encodeURIComponent(id) + "/" + action,
    method: "post",
    data,
  });
export const importResume = (data) =>
  request({
    url: "/simrecrut/resumes/import",
    method: "post",
    data,
    timeout: 60000,
  });
