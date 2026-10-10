<!-- Show record details and confirm session-only administrative changes. -->
<script>
import { useWorkspace } from "../state/workspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <div v-if="drawer" class="overlay" @click.self="drawer = null">
    <section
      class="drawer"
      role="dialog"
      aria-modal="true"
      aria-labelledby="drawer-title"
    >
      <div class="panel-title">
        <h2 id="drawer-title">
          {{
            drawer.kind === "release"
              ? "Confirm model transition"
              : drawer.kind === "retry"
                ? "Queue retry"
                : drawer.kind === "user"
                  ? "Account details"
                  : "Record details"
          }}
        </h2>
        <button class="close" @click="drawer = null" aria-label="Close details">
          ×
        </button>
      </div>
      <div class="panel-body">
        <div class="key-grid single">
          <div v-for="[k, v] in Object.entries(drawer.item)">
            <small>{{ k }}</small
            ><strong>{{ v }}</strong>
          </div>
        </div>
        <template v-if="drawer.kind === 'release'"
          ><div class="callout">
            {{ drawer.item.status }} → {{ drawer.next }}. Activation retires the
            previous demo slot of the same type.
          </div>
          <button class="primary" @click="apply">
            Confirm demo transition
          </button></template
        ><template v-if="drawer.kind === 'retry'"
          ><div class="callout">
            A retry acknowledgement means Queued. It does not mean the failure
            is resolved.
          </div>
          <button class="primary" @click="apply">
            Queue demo retry
          </button></template
        ><template v-if="drawer.kind === 'user'"
          ><label
            >Role<select v-model="role">
              <option>Candidate</option>
              <option>HR</option>
              <option>Researcher</option>
              <option>Admin</option>
            </select></label
          >
          <div class="callout">
            Production changes require RuoYi permissions.
          </div>
          <button class="primary" @click="apply">Save demo role</button
          ><button
            class="secondary"
            @click="
              drawer.item.status =
                drawer.item.status === 'Active' ? 'Disabled' : 'Active';
              drawer = null;
              notify('Demo account status changed.');
            "
          >
            Toggle demo account status
          </button></template
        >
      </div>
    </section>
  </div>
</template>
