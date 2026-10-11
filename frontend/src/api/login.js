// Use the identity endpoints supplied by the official RuoYi backend.
import request from "@/utils/request";
export const login = (data) =>
  request({ url: "/login", method: "post", data, headers: { isToken: false } });
export const getInfo = () => request({ url: "/getInfo", method: "get" });
export const logout = () => request({ url: "/logout", method: "post" });
export const getRouters = () => request({ url: "/getRouters", method: "get" });

// Fetch the configured RuoYi login challenge.
export const getCaptcha = () =>
  request({ url: "/captchaImage", method: "get", headers: { isToken: false } });
