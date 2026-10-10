<!-- Render common workspace navigation for all feature pages. -->
<script>
import { useWorkspace } from "../state/workspace.js";
export default { setup: useWorkspace };
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
    <a
      class="brand"
      href="/research/experiments"
      @click.prevent="go('/research/experiments')"
      ><span class="logo">S</span>SimRecrut</a
    >
    <nav class="topnav">
      <button
        v-for="g in ['Research', 'Review', 'Administration']"
        :class="{ active: page[1] === g }"
        @click="go(pages.find((p) => p[1] === g)[3])"
      >
        {{ g }}
      </button>
    </nav>
    <div class="account">
      <span class="demo-tag">Prototype</span><span class="avatar">YW</span
      ><span
        >Workspace user<small>{{ page[1] }}</small></span
      >
    </div>
  </header>
  <aside class="sidebar" :class="{ open: menu }">
    <div class="sidebar-title">WORKSPACES</div>
    <section v-for="g in ['Research', 'Review', 'Administration']">
      <h3>{{ g }}</h3>
      <button
        v-for="p in pages.filter((p) => p[1] === g)"
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
