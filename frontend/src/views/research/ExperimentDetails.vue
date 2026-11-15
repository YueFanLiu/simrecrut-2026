<!-- R03: Inspect frozen settings, subject selection and experiment stages. -->
<script>
import { useWorkspace } from "../../composables/useResearchWorkspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <section class="panel">
    <div class="panel-title">
      <div>
        <h2>{{ experiment.name }}</h2>
        <span>{{ experiment.id }} · {{ experiment.job }}</span>
      </div>
      <span class="badge" :class="badge(experiment.status)">{{ $t(experiment.status) }}</span>
    </div>
    <div class="tabs">
      <button
        v-for="t in [
          'Configuration',
          'Subjects',
          'Variants',
          'Execution',
          'Comparison',
          'Human Reviews',
        ]"
        :class="{ active: tab === t }"
        @click="tab = t"
      >
        {{ $t(t) }}
      </button>
    </div>
    <div class="panel-body" v-if="tab === 'Configuration'">
      <div class="key-grid">
        <div
          v-for="[k, v] in Object.entries({
            Type: experiment.type,
            'Job version': experiment.job,
            Weights: experiment.weight || 'Unavailable',
            Protocol: experiment.protocol,
            'Neutral model': experiment.neutral || 'Unavailable',
            'Biased model': experiment.biased || 'Unavailable',
          })"
        >
          <small>{{ k }}</small
          ><strong>{{ v }}</strong>
        </div>
      </div>
      <div class="callout"> {{ $t("Frozen configuration · Subject grants are rechecked before execution.") }} </div>
    </div>
    <div class="panel-body" v-else-if="tab === 'Subjects'">
      <h3>{{ $t("Eligible subjects") }}</h3>
      <div class="select-row" v-for="s in data.subjects">
        <label
          ><input
            type="checkbox"
            :checked="selected.includes(s.id)"
            :disabled="s.status !== 'Eligible' || experiment.status !== 'Draft'"
            @change="select(s.id)"
          />{{ s.id }} · {{ s.name }}</label
        ><span class="badge">{{ $t(s.status) }}</span>
      </div>
      <button
        class="primary"
        :disabled="!selected.length || experiment.status !== 'Draft'"
        @click="saveSubjects"
      > {{ $t("Save selection (") }}{{ selected.length }})
      </button>
    </div>
    <div class="panel-body" v-else-if="tab === 'Variants'">
      <h3>{{ $t("Single-factor controlled pairs") }}</h3>
      <p> {{ $t("Professional facts stay identical. Only one declared attribute changes in each pair.") }} </p>
      <label>{{ $t("Controlled attribute") }}<select v-model="factor" :disabled="experiment.status !== 'Draft'"><option v-for="value in factors" :key="value" :value="value">{{ $t(value) }}</option></select></label>
      <button class="primary" :disabled="experiment.status !== 'Draft' || experiment.type !== 'Single-factor paired'" @click="createVariants">{{ $t("Create Demo Variants") }}</button>
      <div class="table-wrap"><table><thead><tr><th>{{ $t("Pair") }}</th><th>{{ $t("Attribute") }}</th><th>{{ $t("Baseline") }}</th><th>{{ $t("Variant") }}</th><th>{{ $t("Professional block") }}</th></tr></thead><tbody><tr v-for="pair in experiment.pairs" :key="pair.id"><td>{{ pair.id }}</td><td>{{ $t(pair.factor) }}</td><td>{{ pair.baseline }}</td><td>{{ pair.variant }}</td><td>{{ $t("Identical") }}</td></tr><tr v-if="!experiment.pairs.length"><td colspan="5">{{ $t("No saved controlled pairs. Baseline experiments do not require variants.") }}</td></tr></tbody></table></div>
      <div class="callout"> {{ $t("Unknown attributes remain explicit and are not inferred from resumes.") }} </div>
    </div>
    <div class="panel-body" v-else-if="tab === 'Execution'">
      <h3>{{ $t("Execution stages") }}</h3>
      <button class="primary" :disabled="experiment.status !== 'Draft'" @click="run">{{ $t("Run Synthetic Demo") }}</button>
      <p>{{ $t("Local validation produces fixed illustrative outputs only. No trained model or real hiring decision is involved.") }}</p>
      <div
        class="stage"
        v-for="t in [
          'Validate grants and frozen versions',
          'Build controlled pairs',
          'Objective Rule',
          'Neutral ML',
          'Biased ML',
          'Human review assignments',
        ]"
      >
        <span>○</span><strong>{{ $t(t) }}</strong
        ><span class="badge">{{ $t(experiment.outputs?.length && t !== 'Human review assignments' ? 'Demo complete' : 'Not connected') }}</span>
      </div>
      <div class="callout">
        {{ experiment.execution }}
      </div>
    </div>
    <div class="panel-body" v-else-if="tab === 'Comparison'">
      <h3>{{ $t("Within-method paired comparison") }}</h3>
      <div class="table-wrap"><table>
        <thead>
          <tr>
            <th>{{ $t("Method") }}</th>
            <th>{{ $t("Valid pairs") }}</th>
            <th>{{ $t("Decision flips") }}</th>
            <th>{{ $t("Flip rate") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(m, key) in { objective: 'Objective Rule', neutral: 'Neutral ML', biased: 'Biased ML' }" :key="key">
            <td>{{ m }}</td>
            <td>{{ $t(experiment.outputs?.length ? resultSummary[key].pairs : '—') }}</td>
            <td>{{ $t(experiment.outputs?.length ? resultSummary[key].flips : '—') }}</td>
            <td>{{ $t(resultSummary[key].flipRate === null ? 'Unavailable' : (resultSummary[key].flipRate * 100).toFixed(1) + '%') }}</td>
          </tr>
        </tbody>
      </table></div>
      <p class="muted"> {{ $t("Synthetic fixtures only; no scientific evidence of bias. Scores from different methods are not compared directly.") }} </p>
      <button
        class="secondary"
        @click="go('/research/metrics?experimentId=' + experiment.id)"
      > {{ $t("View evaluation metrics →") }} </button>
    </div>
    <div class="panel-body" v-else>
      <h3>{{ $t("Human review assignments") }}</h3>
      <div class="toolbar"><label>{{ $t("Eligible demo reviewer") }}<select v-model="reviewer"><option value="reviewer-hr">{{ $t("Demo HR Reviewer") }}</option><option value="reviewer-researcher">{{ $t("Demo Research Reviewer") }}</option></select></label><label>{{ $t("Review condition") }}<select v-model="condition"><option value="Standard">{{ $t("Standard") }}</option><option value="Instructed-Bias">{{ $t("Instructed-Bias") }}</option></select></label><button class="primary" @click="assign">{{ $t("Assign Demo Research Reviews") }}</button></div>
      <p>{{ $t("Assignments are Research reviews with Pending status, not Candidate-visible Primary reviews. No notification is sent.") }}</p>
      <div class="table-wrap"><table>
        <thead>
          <tr>
            <th>{{ $t("Task") }}</th>
            <th>{{ $t("Mode") }}</th>
            <th>{{ $t("Target") }}</th><th>{{ $t("Purpose") }}</th>
            <th>{{ $t("Status") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in experiment.reviews" :key="t.id">
            <td>{{ t.id }}</td>
            <td>{{ $t(t.condition) }}</td>
            <td>{{ t.target }}</td><td>{{ $t(t.purpose) }}</td>
            <td>{{ $t(t.status) }}</td>
          </tr>
        </tbody>
      </table></div>
      <p class="muted"> {{ $t("Decisions and reasons require permitted result projections.") }} </p>
    </div>
  </section>
</template>
