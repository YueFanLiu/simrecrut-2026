// Read identity and grants from the Java JWT session; demo privileges never authorize API writes.
import { defineStore } from "pinia";
import { login, getInfo, logout } from "@/api/login";
import { getToken, setToken, removeToken } from "@/utils/auth";
import { demoMode } from "../../config/runtime.js";
export { demoMode };
export default defineStore("user", {
  state: () => ({
    token: getToken() || "",
    roles: [],
    permissions: [],
    name: "",
    username: "",
    loaded: false,
  }),
  actions: {
    async Login(credentials) {
      const response = await login(credentials);
      if (!response.data?.accessToken)
        throw new Error("The identity service did not return a token.");
      this.token = response.data.accessToken;
      setToken(this.token);
      try { await this.GetInfo(); }
      catch (error) { removeToken(); this.$reset(); this.token = ''; throw error; }
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
      this.roles = (response.data?.roles || []).map(role => role.toLowerCase());
      this.permissions = response.data?.permissions || [];
      this.name = response.data?.username || "Workspace user";
      this.username = response.data?.username || "";
      this.loaded = true;
    },
    async LogOut() {
      try {
        if (!demoMode && this.token) await logout();
      } finally {
        removeToken();
        this.$reset();
        this.token = '';
      }
    },
  },
});
