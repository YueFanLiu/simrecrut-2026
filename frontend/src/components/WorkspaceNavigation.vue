<!-- Render common workspace navigation for all feature pages. -->
<script>
import { computed } from "vue";
import { useRouter } from "vue-router";
import { demoMode } from "../store/modules/user";
import useUserStore from "../store/modules/user";
import { permissionFor } from "../router/permissions";
import { useWorkspace } from "../state/workspace.js";
export default {
  setup() {
    const user = useUserStore();
    const router = useRouter();
    const workspace = useWorkspace();
    const visible = (id) =>
      user.permissions.includes("*:*:*") ||
      user.permissions.includes(permissionFor(id));
    return {
      ...workspace,
      groups: computed(() =>
        ["Candidate", "Research", "Review", "Administration"].filter((group) =>
          workspace.pages.some((page) => page[1] === group && visible(page[0])),
        ),
      ),
      home: () =>
        workspace.go(
          workspace.pages.find((page) => visible(page[0]))?.[3] || "/forbidden",
        ),
      user,
      demoMode,
      signOut: async () => {
        await user.LogOut().catch(() => {});
        router.replace("/login");
      },
      visible: (id) =>
        user.permissions.includes("*:*:*") ||
        user.permissions.includes(permissionFor(id)),
    };
  },
};
</script>

<template>
  <header class="topbar">
    <button
      class="menu-toggle"
      @click="menu = !menu"
      aria-label="Toggle navigation"
    >
      ☰
    </button>
    <a class="brand" href="/research/experiments" @click.prevent="home"
      ><span class="logo">S</span>SimRecrut</a
    >
    <nav class="topnav">
      <button
        v-for="g in groups"
        :class="{ active: page[1] === g }"
        @click="go(pages.find((p) => p[1] === g)[3])"
      >
        {{ g }}
      </button>
    </nav>
    <div class="account">
      <span class="demo-tag">{{ demoMode ? "Prototype" : "RuoYi" }}</span
      ><span class="avatar">YW</span
      ><span
        >{{ user.name }}<small>{{ page[1] }}</small></span
      >
      <button v-if="!demoMode" @click="signOut">Sign out</button>
    </div>
  </header>
  <aside class="sidebar" :class="{ open: menu }">
    <div class="sidebar-title">WORKSPACES</div>
    <section v-for="g in groups">
      <h3>{{ g }}</h3>
      <button
        v-for="p in pages.filter((p) => p[1] === g && visible(p[0]))"
        :class="{ active: page[0] === p[0] }"
        @click="go(p[3])"
      >
        <span class="nav-code">{{ p[0] }}</span
        >{{ p[2] }}
      </button>
    </section>
    <div class="sidebar-foot">
      <span class="live-dot"></span>UI development preview<small
        >Java 17 backend · Vue 3 frontend</small
      >
    </div>
  </aside>
</template>
