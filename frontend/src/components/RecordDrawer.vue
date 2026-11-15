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
          {{ $t(drawer.kind === "release"
              ? "Confirm model transition"
              : drawer.kind === "retry"
                ? "Queue retry"
                : drawer.kind === "user"
                  ? "Account details"
                  : "Record details") }}
        </h2>
        <button class="close" @click="drawer = null" :aria-label="$t(&quot;Close details&quot;)">
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
            {{ $t(drawer.item.status) }} → {{ drawer.next }}{{ $t(". Activation retires the previous demo slot of the same type.") }} </div>
          <button class="primary" @click="apply"> {{ $t("Confirm demo transition") }} </button></template
        ><template v-if="drawer.kind === 'retry'"
          ><div class="callout"> {{ $t("A retry acknowledgement means Queued. It does not mean the failure is resolved.") }} </div>
          <button class="primary" @click="apply"> {{ $t("Queue demo retry") }} </button></template
        ><template v-if="drawer.kind === 'user'"
          ><label
            >{{ $t("Role") }}<select v-model="role">
              <option value="Candidate">{{ $t("Candidate") }}</option>
              <option value="HR">{{ $t("HR") }}</option>
              <option value="Researcher">{{ $t("Researcher") }}</option>
              <option value="Admin">{{ $t("Admin") }}</option>
            </select></label
          >
          <div class="callout"> {{ $t("Production changes require authorization from the server.") }} </div>
          <button class="primary" @click="apply">{{ $t("Save demo role") }}</button
          ><button
            class="secondary"
            @click="
              drawer.item.status =
                drawer.item.status === 'Active' ? 'Disabled' : 'Active';
              drawer = null;
              notify('Demo account status changed.');
            "
          > {{ $t("Toggle demo account status") }} </button></template
        >
      </div>
    </section>
  </div>
</template>
