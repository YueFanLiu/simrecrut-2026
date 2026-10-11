// Identity and grants follow RuoYi-Vue3; demo privileges never become backend credentials.
import { defineStore } from "pinia";
import { login, getInfo, logout } from "@/api/login";
import { getToken, setToken, removeToken } from "@/utils/auth";
export const demoMode = import.meta.env.VITE_APP_DEMO_MODE === "true";
export default defineStore("user", {
  state: () => ({
    token: getToken() || "",
    roles: [],
    permissions: [],
    name: "",
    loaded: false,
  }),
  actions: {
    async Login(credentials) {
      const response = await login(credentials);
      if (!response.token)
        throw new Error("The identity service did not return a token.");
      this.token = response.token;
      setToken(response.token);
      await this.GetInfo();
    },
    async GetInfo() {
      if (demoMode) {
        this.name = "Demo workspace";
        this.roles = ["admin"];
        this.permissions = ["*:*:*"];
        this.loaded = true;
        return;
      }
      const response = await getInfo();
      this.roles = response.roles || [];
      this.permissions = response.permissions || [];
      this.name =
        response.user?.nickName || response.user?.userName || "Workspace user";
      this.loaded = true;
    },
    async LogOut() {
      try {
        if (!demoMode && this.token) await logout();
      } finally {
        removeToken();
        this.$reset();
      }
    },
  },
});
