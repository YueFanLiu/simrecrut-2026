<!-- R03: Inspect frozen settings, subject selection and experiment stages. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
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
            Weights: experiment.weight || 'Balanced professional v1',
            Protocol: experiment.protocol,
            'Neutral model': experiment.neutral || 'N-2026.01',
            'Biased model': experiment.biased || 'B-2026.01',
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
        @click="
          experiment.subjectIds = [...selected];
          experiment.subjects = selected.length;
          notify('Demo subject selection saved.');
        "
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
      <table>
        <thead>
          <tr>
            <th>Pair</th>
            <th>Attribute</th>
            <th>Baseline</th>
            <th>Variant</th>
            <th>Professional block</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>PAIR-001</td>
            <td>Declared gender</td>
            <td>Category A</td>
            <td>Category B</td>
            <td><span class="badge completed">Identical</span></td>
          </tr>
        </tbody>
      </table>
      <div class="callout">
        Unknown attributes remain explicit and are not inferred from resumes.
      </div>
    </div>
    <div class="panel-body" v-else-if="tab === 'Execution'">
      <h3>Execution stages</h3>
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
        ><span class="badge">Not connected</span>
      </div>
      <div class="callout">
        Production execution is unavailable in this prototype.
      </div>
    </div>
    <div class="panel-body" v-else-if="tab === 'Comparison'">
      <h3>Within-method paired comparison</h3>
      <table>
        <thead>
          <tr>
            <th>Method</th>
            <th>Valid pairs</th>
            <th>Decision flips</th>
            <th>Flip rate</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in ['Objective Rule', 'Neutral ML', 'Biased ML']">
            <td>{{ m }}</td>
            <td>—</td>
            <td>—</td>
            <td>Unavailable</td>
          </tr>
        </tbody>
      </table>
      <p class="muted">
        No real comparison data. Scores from different methods are not compared
        directly.
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
      <table>
        <thead>
          <tr>
            <th>Task</th>
            <th>Mode</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in data.tasks">
            <td>{{ t.id }}</td>
            <td>{{ t.mode }}</td>
            <td>{{ t.status }}</td>
          </tr>
        </tbody>
      </table>
      <p class="muted">
        Decisions and reasons require permitted result projections.
      </p>
    </div>
  </section>
</template>
