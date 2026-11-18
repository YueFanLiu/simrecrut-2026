<!-- R02: Collect seven required settings before creating a draft. -->
<script>
import { useWorkspace } from "../../composables/useResearchWorkspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <section class="panel form-panel">
    <div class="panel-title">
      <h2>{{ $t("Experiment configuration") }}</h2>
      <span>{{ $t("All seven fields are required") }}</span>
    </div>
    <form novalidate @submit.prevent="create">
      <div class="form-grid">
        <label class="full"
          >{{ $t("Name") }} <em>*</em
          ><input
            v-model="form.name"
            maxlength="120"
            :placeholder="$t(&quot;e.g. Controlled gender attribute study&quot;)"
            required
        /></label>
        <label
          >{{ $t("Experiment type") }} <em>*</em
          ><select v-model="form.type" required>
            <option disabled value="">{{ $t("Select type") }}</option>
            <option value="Single-factor paired">{{ $t("Single-factor paired") }}</option>
            <option value="Baseline">{{ $t("Baseline") }}</option>
          </select></label
        >
        <label
          >{{ $t("Job version") }} <em>*</em
          ><select v-model="form.job" required>
            <option disabled value="">{{ $t("Select published version") }}</option>
            <option v-for="row in researchJobs" :key="row.id">{{ row.id }}</option>
          </select></label
        >
        <label
          >{{ $t("Weight configuration") }} <em>*</em
          ><select v-model="form.weight" required>
            <option disabled value="">{{ $t("Select frozen weights") }}</option>
            <option value="Balanced professional v1">{{ $t("Balanced professional v1") }}</option>
          </select></label
        >
        <label
          >{{ $t("Protocol") }} <em>*</em
          ><select v-model="form.protocol" required>
            <option disabled value="">{{ $t("Select protocol") }}</option>
            <option>P-2026.01</option>
          </select></label
        >
        <label
          >{{ $t("Neutral model") }} <em>*</em
          ><select v-model="form.neutral" required>
            <option disabled value="">{{ $t("Select compatible model") }}</option>
            <option v-for="model in data.models.filter(row => row.name === 'Neutral ML' && row.status === 'Active')" :key="model.id">{{ model.id }} / {{ model.version }}</option>
          </select></label
        >
        <label
          >{{ $t("Biased model") }} <em>*</em
          ><select v-model="form.biased" required>
            <option disabled value="">{{ $t("Select compatible model") }}</option>
            <option v-for="model in data.models.filter(row => row.name === 'Biased ML' && row.status === 'Active')" :key="model.id">{{ model.id }} / {{ model.version }}</option>
          </select></label
        >
      </div>
      <div class="callout"> {{ $t("Configuration is frozen for the experiment. Creating a draft does not run models or upload CV files.") }} </div>
      <p class="error" v-if="error">{{ $t(error) }}</p>
      <div class="form-actions">
        <button
          type="button"
          class="secondary"
          @click="go('/research/experiments')"
        > {{ $t("Cancel") }}</button
        ><button class="primary">{{ $t("Create draft experiment →") }}</button>
      </div>
    </form>
  </section>
</template>
