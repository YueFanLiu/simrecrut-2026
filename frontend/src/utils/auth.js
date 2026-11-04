/** Token helpers adapted from RuoYi-Vue3. Copyright (c) 2018 RuoYi; MIT. */
import Cookies from "js-cookie";
const TokenKey = "Admin-Token";
export const getToken = () => Cookies.get(TokenKey);
export const setToken = (token) =>
  Cookies.set(TokenKey, token, {
    sameSite: "strict",
    secure: location.protocol === "https:",
  });
export const removeToken = () => Cookies.remove(TokenKey);
