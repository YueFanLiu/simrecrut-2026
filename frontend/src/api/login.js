// Public identity calls never attach or invalidate an existing session.
import request from "@/utils/request";
export const login = (data) =>
  request({ url: "/api/v1/auth/login", method: "post", data, headers: { isToken: false }, silent: true });
export const sendRegistrationCode = data => request({
  url: "/api/v1/auth/registration/code", method: "post", data,
  headers: { isToken: false }, silent: true, timeout: 35000,
});
export const register = data => request({ url: "/api/v1/auth/register", method: "post", data, headers: { isToken: false }, silent: true });
export const getInfo = () => request({ url: "/api/v1/auth/me", method: "get" });
export const logout = () => request({ url: "/api/v1/auth/logout", method: "post" });
export const getRouters = () => request({ url: "/getRouters", method: "get" });

// Fetch the configured RuoYi login challenge.
export const getCaptcha = () =>
  request({ url: "/captchaImage", method: "get", headers: { isToken: false } });

// Email verification and password reset use public endpoints without a login token.
export const passwordResetOptions = () => request({ url: '/simrecrut/account/password/options', method: 'get', headers: { isToken: false }, silent: true });
export const sendPasswordCode = data => request({ url: '/simrecrut/account/password/code', method: 'post', data, headers: { isToken: false }, silent: true, timeout: 35000 });
export const resetPassword = data => request({ url: '/simrecrut/account/password/reset', method: 'post', data, headers: { isToken: false }, silent: true });
