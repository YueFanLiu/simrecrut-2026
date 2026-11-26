<!-- A05: Show unknown service health until real health checks are connected. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <div class="stats">
    <article>
      <small>SERVICE CHECKS</small><b>5</b
      ><span>Expected runtime dependencies</span>
    </article>
    <article>
      <small>CONNECTED</small><b>—</b><span>No health API connected</span>
    </article>
    <article>
      <small>MODEL SLOTS</small><b>2</b><span>Neutral ML / Biased ML</span>
    </article>
    <article>
      <small>LAST CHECK</small><b class="small-number">Unavailable</b
      ><span>Freshness cannot be verified</span>
    </article>
  </div>
  <section class="panel">
    <div class="panel-title">
      <h2>Runtime services</h2>
      <span>No fabricated uptime values</span>
    </div>
    <table>
      <thead>
        <tr>
          <th>Service</th>
          <th>Status</th>
          <th>Checked at</th>
          <th>Freshness</th>
          <th>Details</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="s in [
            'Java API',
            'MySQL',
            'Redis',
            'ML inference',
            'Temporary workspace / cleanup',
          ]"
        >
          <td>{{ s }}</td>
          <td><span class="badge">Unknown</span></td>
          <td>—</td>
          <td>Unavailable</td>
          <td>Health contract not connected</td>
        </tr>
      </tbody>
    </table>
  </section>
  <section class="panel">
    <div class="panel-title"><h2>Loaded model slots</h2></div>
    <div class="key-grid panel-body">
      <div v-for="m in ['Neutral ML', 'Biased ML']">
        <small>{{ m }}</small
        ><strong>Load status unknown</strong>
      </div>
    </div>
  </section>
</template>
