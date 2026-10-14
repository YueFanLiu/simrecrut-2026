<!-- A02: Edit demo menu permissions independently of individual case grants. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <section class="panel">
    <div class="panel-title">
      <h2>Role permission configuration</h2>
      <span>Demo permissions only</span>
    </div>
    <div class="panel-body permissions">
      <div>
        <button
          v-for="r in ['Candidate', 'HR', 'Researcher', 'Admin']"
          class="role-row"
          :class="{ active: role === r }"
          @click="role = r"
        >
          {{ r }}
        </button>
      </div>
      <div>
        <h3>{{ role }} permissions</h3>
        <label
          class="check"
          v-for="p in [
            'Candidate workspace',
            'HR workspace',
            'Research workspace',
            'View experiments',
            'Create experiments',
            'View models',
            'Review assigned tasks',
            'Administration',
            'View users',
            'Manage model releases',
            'Read audit logs',
          ]"
          ><input
            type="checkbox"
            v-model="rolePermissions[role]"
            :value="p"
          />{{ p }}</label
        >
        <div class="callout">
          Reviewer is an assigned responsibility, not a separate privileged
          role. Menu permissions do not grant access to individual cases.
        </div>
        <button
          class="primary"
          @click="
            notify('Demo permission selection saved for this session only.')
          "
        >
          Save demo permissions
        </button>
      </div>
    </div>
  </section>
</template>
