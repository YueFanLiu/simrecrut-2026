/** Authenticated JSON transport using the RuoYi-Vue3 interceptor convention. */
import axios from "axios";
import { ElMessage } from "element-plus";
import { getToken, removeToken } from "./auth";

const request = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || "/dev-api",
  timeout: 15000,
});
request.interceptors.request.use((config) => {
  if (config.headers.isToken !== false && getToken())
    config.headers.Authorization = `Bearer ${getToken()}`;
  delete config.headers.isToken;
  return config;
});
request.interceptors.response.use(
  (response) => {
    const body = response.data;
    const code = body.code ?? 200;
    if (code === 0 || code === 200) return body;
    if (code === 401) {
      removeToken();
      if (location.pathname !== "/login") location.assign("/login");
    }
    const message =
      body.msg || body.message || "The request could not be completed.";
    ElMessage.error(message);
    return Promise.reject(new Error(message));
  },
  (error) => {
    if (error.response?.status === 401) {
      removeToken();
      if (location.pathname !== "/login") location.assign("/login");
    }
    ElMessage.error(
      error.response?.data?.message ||
        "The service is unavailable. Please retry.",
    );
    return Promise.reject(error);
  },
);
export default request;
