<!-- A05: Show unknown service health until real health checks are connected. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <div class="stats">
    <article>
      <small>{{ $t("SERVICE CHECKS") }}</small><b>5</b
      ><span>{{ $t("Expected runtime dependencies") }}</span>
    </article>
    <article>
      <small>{{ $t("CONNECTED") }}</small><b>—</b><span>{{ $t("No health API connected") }}</span>
    </article>
    <article>
      <small>{{ $t("MODEL SLOTS") }}</small><b>2</b><span>{{ $t("Neutral ML / Biased ML") }}</span>
    </article>
    <article>
      <small>{{ $t("LAST CHECK") }}</small><b class="small-number">{{ $t("Unavailable") }}</b
      ><span>{{ $t("Freshness cannot be verified") }}</span>
    </article>
  </div>
  <section class="panel">
    <div class="panel-title">
      <h2>{{ $t("Runtime services") }}</h2>
      <span>{{ $t("No fabricated uptime values") }}</span>
    </div>
    <table>
      <thead>
        <tr>
          <th>{{ $t("Service") }}</th>
          <th>{{ $t("Status") }}</th>
          <th>{{ $t("Checked at") }}</th>
          <th>{{ $t("Freshness") }}</th>
          <th>{{ $t("Details") }}</th>
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
          <td><span class="badge">{{ $t("Unknown") }}</span></td>
          <td>—</td>
          <td>{{ $t("Unavailable") }}</td>
          <td>{{ $t("Health contract not connected") }}</td>
        </tr>
      </tbody>
    </table>
  </section>
  <section class="panel">
    <div class="panel-title"><h2>{{ $t("Loaded model slots") }}</h2></div>
    <div class="key-grid panel-body">
      <div v-for="m in ['Neutral ML', 'Biased ML']">
        <small>{{ m }}</small
        ><strong>{{ $t("Load status unknown") }}</strong>
      </div>
    </div>
  </section>
</template>
