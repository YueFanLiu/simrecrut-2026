import { t, language, languageOptions } from '../i18n/index.js'
/** Authenticated JSON transport using the RuoYi-Vue3 interceptor convention. */
import axios from "axios";
import { ElMessage } from "element-plus";
import { getToken, removeToken } from "./auth";
import { requestErrorMessage } from "./ui-message.js";

const request = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || "/dev-api",
  timeout: 15000,
});
request.interceptors.request.use((config) => {
  config.publicRequest = config.headers.isToken === false;
  config.headers["Accept-Language"] = languageOptions.find(option => option.value === language.value).lang;
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
    if (code === 401 && !response.config.publicRequest) {
      removeToken();
      if (location.pathname !== "/login") location.assign("/login");
    }
    const message = requestErrorMessage({ response: { status: code, data: body } });
    if (!response.config.silent) ElMessage.error(t(message));
    return Promise.reject(Object.assign(new Error(message), {
      config: response.config,
      response: { ...response, status: code },
    }));
  },
  (error) => {
    if (axios.isCancel(error)) return Promise.reject(error);
    if (error.response?.status === 401 && !error.config?.publicRequest) {
      removeToken();
      if (location.pathname !== "/login") location.assign("/login");
    }
    error.message = requestErrorMessage(error);
    if (!error.config?.silent) ElMessage.error(t(error.message));
    return Promise.reject(error);
  },
);
export default request;
