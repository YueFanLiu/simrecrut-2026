// Bind independent business pages to RuoYi permission metadata.
import { createRouter, createWebHistory } from "vue-router";
import Layout from "@/layout/index.vue";
import { pages } from "./pages";
import { pageComponents } from "./pageComponents";
import useUserStore, { demoMode } from "@/store/modules/user";
import { permissionFor } from "./permissions";
const businessRoutes = pages.map((p) => ({
  path:
    p[0] === "C04"
      ? "/candidate/resumes/:id"
      : p[0] === "R03"
        ? "/research/experiments/:id"
        : p[0] === "V02"
          ? "/reviewer/tasks/:id"
          : p[3].split("?")[0],
  name: p[0],
  component: pageComponents[p[0]],
  meta: { title: p[4], permissions: [permissionFor(p[0])], pageId: p[0] },
}));
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: "/login",
      component: () => import("@/views/auth/Login.vue"),
      meta: { public: true },
    },
    {
      path: "/",
      component: Layout,
      redirect: "/research/experiments",
      children: businessRoutes,
    },
    {
      path: "/:pathMatch(.*)*",
      component: () => import("@/views/auth/NotFound.vue"),
      meta: { public: true },
    },
  ],
});
router.beforeEach(async (to) => {
  const user = useUserStore();
  if (to.meta.public) return true;
  if (!demoMode && !user.token)
    return { path: "/login", query: { redirect: to.fullPath } };
  try {
    if (!user.loaded) await user.GetInfo();
  } catch {
    await user.LogOut().catch(() => {});
    return "/login";
  }
  if (
    !user.permissions.includes("*:*:*") &&
    !(to.meta.permissions || []).some((p) => user.permissions.includes(p))
  )
    return "/forbidden";
  return true;
});
export default router;
