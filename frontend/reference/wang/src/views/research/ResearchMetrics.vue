<!-- R06: Keep missing evaluation metrics explicit instead of inventing values. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <div class="toolbar panel">
    <label
      >Experiment<select
        :value="path.split('=')[1] || 'EXP-001'"
        @change="go('/research/metrics?experimentId=' + $event.target.value)"
      >
        <option v-for="e in data.experiments" :value="e.id">
          {{ e.name }}
        </option>
      </select></label
    ><span class="badge">Real metrics unavailable</span>
  </div>
  <div class="metrics-grid">
    <section class="panel" v-for="m in ['Neutral ML', 'Biased ML']">
      <div class="panel-title">
        <h2>{{ m }}</h2>
        <span>Held-out evaluation</span>
      </div>
      <div class="panel-body">
        <div class="mini-stats">
          <div v-for="v in ['Accuracy', 'Precision', 'Recall', 'F1']">
            <small>{{ v }}</small
            ><b>—</b>
          </div>
        </div>
        <h3>Confusion matrix</h3>
        <div class="matrix">
          <span></span><span>Predicted reject</span><span>Predicted accept</span
          ><span>Actual reject</span><b>—<small>True negative</small></b
          ><b>—<small>False positive</small></b
          ><span>Actual accept</span><b>—<small>False negative</small></b
          ><b>—<small>True positive</small></b>
        </div>
        <p class="muted">Sample size and evaluation provenance unavailable.</p>
      </div>
    </section>
  </div>
  <section class="panel">
    <div class="panel-title">
      <h2>Group fairness & ranking</h2>
      <span>Explicit denominators required</span>
    </div>
    <table>
      <thead>
        <tr>
          <th>Group</th>
          <th>Eligible N</th>
          <th>Evaluated N</th>
          <th>Acceptance rate</th>
          <th>Top-K</th>
          <th>Flip rate</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="g in ['Category A', 'Category B', 'Unknown']">
          <td>{{ g }}</td>
          <td>—</td>
          <td>—</td>
          <td>Unavailable</td>
          <td>Unavailable</td>
          <td>Unavailable</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
