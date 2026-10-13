// Use the identity endpoints supplied by the official RuoYi backend.
import request from "@/utils/request";
export const login = (data) =>
  request({ url: "/simrecrut/account/login", method: "post", data, headers: { isToken: false } });
export const getRegistrationOptions = () => request({ url: "/simrecrut/account/options", method: "get", headers: { isToken: false }, silent: true });
export const register = data => request({ url: "/simrecrut/account/register", method: "post", data, headers: { isToken: false }, silent: true });
export const getInfo = () => request({ url: "/getInfo", method: "get" });
export const logout = () => request({ url: "/logout", method: "post" });
export const getRouters = () => request({ url: "/getRouters", method: "get" });

// Fetch the configured RuoYi login challenge.
export const getCaptcha = () =>
  request({ url: "/captchaImage", method: "get", headers: { isToken: false } });

// Email verification and password reset use public endpoints without a login token.
export const passwordResetOptions = () => request({ url: '/simrecrut/account/password/options', method: 'get', headers: { isToken: false }, silent: true });
export const sendPasswordCode = data => request({ url: '/simrecrut/account/password/code', method: 'post', data, headers: { isToken: false }, silent: true, timeout: 35000 });
export const resetPassword = data => request({ url: '/simrecrut/account/password/reset', method: 'post', data, headers: { isToken: false }, silent: true });
