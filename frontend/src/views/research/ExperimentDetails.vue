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
      <span class="badge" :class="badge(experiment.status)">{{
        experiment.status
      }}</span>
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
        {{ t }}
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
      <div class="callout">
        Frozen configuration · Subject grants are rechecked before execution.
      </div>
    </div>
    <div class="panel-body" v-else-if="tab === 'Subjects'">
      <h3>Eligible subjects</h3>
      <div class="select-row" v-for="s in data.subjects">
        <label
          ><input
            type="checkbox"
            :checked="selected.includes(s.id)"
            :disabled="s.status !== 'Eligible' || experiment.status !== 'Draft'"
            @change="select(s.id)"
          />{{ s.id }} · {{ s.name }}</label
        ><span class="badge">{{ s.status }}</span>
      </div>
      <button
        class="primary"
        :disabled="!selected.length || experiment.status !== 'Draft'"
        @click="saveSubjects"
      >
        Save selection ({{ selected.length }})
      </button>
    </div>
    <div class="panel-body" v-else-if="tab === 'Variants'">
      <h3>Single-factor controlled pairs</h3>
      <p>
        Professional facts stay identical. Only one declared attribute changes
        in each pair.
      </p>
      <label>Controlled attribute<select v-model="factor" :disabled="experiment.status !== 'Draft'"><option v-for="value in factors" :key="value">{{ value }}</option></select></label>
      <button class="primary" :disabled="experiment.status !== 'Draft' || experiment.type !== 'Single-factor paired'" @click="createVariants">Create Demo Variants</button>
      <div class="table-wrap"><table><thead><tr><th>Pair</th><th>Attribute</th><th>Baseline</th><th>Variant</th><th>Professional block</th></tr></thead><tbody><tr v-for="pair in experiment.pairs" :key="pair.id"><td>{{ pair.id }}</td><td>{{ pair.factor }}</td><td>{{ pair.baseline }}</td><td>{{ pair.variant }}</td><td>Identical</td></tr><tr v-if="!experiment.pairs.length"><td colspan="5">No saved controlled pairs. Baseline experiments do not require variants.</td></tr></tbody></table></div>
      <div class="callout">
        Unknown attributes remain explicit and are not inferred from resumes.
      </div>
    </div>
    <div class="panel-body" v-else-if="tab === 'Execution'">
      <h3>Execution stages</h3>
      <button class="primary" :disabled="experiment.status !== 'Draft'" @click="run">Run Synthetic Demo</button>
      <p>Local validation produces fixed illustrative outputs only. No trained model or real hiring decision is involved.</p>
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
        <span>○</span><strong>{{ t }}</strong
        ><span class="badge">{{ experiment.outputs?.length && t !== 'Human review assignments' ? 'Demo complete' : 'Not connected' }}</span>
      </div>
      <div class="callout">
        {{ experiment.execution }}
      </div>
    </div>
    <div class="panel-body" v-else-if="tab === 'Comparison'">
      <h3>Within-method paired comparison</h3>
      <div class="table-wrap"><table>
        <thead>
          <tr>
            <th>Method</th>
            <th>Valid pairs</th>
            <th>Decision flips</th>
            <th>Flip rate</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(m, key) in { objective: 'Objective Rule', neutral: 'Neutral ML', biased: 'Biased ML' }" :key="key">
            <td>{{ m }}</td>
            <td>{{ experiment.outputs?.length ? resultSummary[key].pairs : '—' }}</td>
            <td>{{ experiment.outputs?.length ? resultSummary[key].flips : '—' }}</td>
            <td>{{ resultSummary[key].flipRate === null ? 'Unavailable' : (resultSummary[key].flipRate * 100).toFixed(1) + '%' }}</td>
          </tr>
        </tbody>
      </table></div>
      <p class="muted">
        Synthetic fixtures only; no scientific evidence of bias. Scores from different methods are not compared directly.
      </p>
      <button
        class="secondary"
        @click="go('/research/metrics?experimentId=' + experiment.id)"
      >
        View evaluation metrics →
      </button>
    </div>
    <div class="panel-body" v-else>
      <h3>Human review assignments</h3>
      <div class="toolbar"><label>Eligible demo reviewer<select v-model="reviewer"><option value="reviewer-hr">Demo HR Reviewer</option><option value="reviewer-researcher">Demo Research Reviewer</option></select></label><label>Review condition<select v-model="condition"><option>Standard</option><option>Instructed-Bias</option></select></label><button class="primary" @click="assign">Assign Demo Research Reviews</button></div>
      <p>Assignments are Research reviews with Pending status, not Candidate-visible Primary reviews. No notification is sent.</p>
      <div class="table-wrap"><table>
        <thead>
          <tr>
            <th>Task</th>
            <th>Mode</th>
            <th>Target</th><th>Purpose</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in experiment.reviews" :key="t.id">
            <td>{{ t.id }}</td>
            <td>{{ t.condition }}</td>
            <td>{{ t.target }}</td><td>{{ t.purpose }}</td>
            <td>{{ t.status }}</td>
          </tr>
        </tbody>
      </table></div>
      <p class="muted">
        Decisions and reasons require permitted result projections.
      </p>
    </div>
  </section>
</template>
