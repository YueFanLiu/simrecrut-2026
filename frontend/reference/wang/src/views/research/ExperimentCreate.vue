<!-- R02: Collect seven required settings before creating a draft. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <section class="panel form-panel">
    <div class="panel-title">
      <h2>Experiment configuration</h2>
      <span>All seven fields are required</span>
    </div>
    <form @submit.prevent="create">
      <div class="form-grid">
        <label class="full"
          >Name <em>*</em
          ><input
            v-model="form.name"
            maxlength="120"
            placeholder="e.g. Controlled gender attribute study"
            required
        /></label>
        <label
          >Experiment type <em>*</em
          ><select v-model="form.type" required>
            <option disabled value="">Select type</option>
            <option>Single-factor paired</option>
            <option>Baseline</option>
          </select></label
        >
        <label
          >Job version <em>*</em
          ><select v-model="form.job" required>
            <option disabled value="">Select published version</option>
            <option>Software Engineer v3</option>
            <option>Data Analyst v2</option>
          </select></label
        >
        <label
          >Weight configuration <em>*</em
          ><select v-model="form.weight" required>
            <option disabled value="">Select frozen weights</option>
            <option>Balanced professional v1</option>
          </select></label
        >
        <label
          >Protocol <em>*</em
          ><select v-model="form.protocol" required>
            <option disabled value="">Select protocol</option>
            <option>P-2026.01</option>
          </select></label
        >
        <label
          >Neutral model <em>*</em
          ><select v-model="form.neutral" required>
            <option disabled value="">Select compatible model</option>
            <option>N-2026.01 / 1.0.0</option>
          </select></label
        >
        <label
          >Biased model <em>*</em
          ><select v-model="form.biased" required>
            <option disabled value="">Select compatible model</option>
            <option>B-2026.01 / 1.0.0</option>
          </select></label
        >
      </div>
      <div class="callout">
        Configuration is frozen for the experiment. Creating a draft does not
        run models or upload CV files.
      </div>
      <p class="error" v-if="error">{{ error }}</p>
      <div class="form-actions">
        <button
          type="button"
          class="secondary"
          @click="go('/research/experiments')"
        >
          Cancel</button
        ><button class="primary">Create draft experiment →</button>
      </div>
    </form>
  </section>
</template>
