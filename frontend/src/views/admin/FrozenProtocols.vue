<!-- A04: Display published protocol versions without editing their definitions. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <section class="panel">
    <div class="panel-title">
      <h2>{{ $t("Published protocol versions") }}</h2>
      <span>{{ $t("Read-only registry") }}</span>
    </div>
    <table>
      <thead>
        <tr>
          <th>{{ $t("Protocol version") }}</th>
          <th>{{ $t("Feature schema") }}</th>
          <th>{{ $t("Cutoff") }}</th>
          <th>{{ $t("Status") }}</th>
          <th>{{ $t("Action") }}</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>P-2026.01</td>
          <td>{{ $t("professional-v1 / 32 inputs") }}</td>
          <td>{{ $t("Unavailable") }}</td>
          <td><span class="badge completed">{{ $t("Frozen") }}</span></td>
          <td>
            <button
              class="text-button"
              @click="
                drawer = {
                  kind: 'protocol',
                  item: {
                    id: 'P-2026.01',
                    schema: 'professional-v1',
                    matching: 'Frozen job requirements and weights',
                    labels: 'Versioned decision label mapping',
                    preprocessing: 'Model-package preprocessing manifest',
                    cutoff: 'Unavailable until backend integration',
                  },
                }
              "
            > {{ $t("View protocol →") }} </button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="panel-body callout"> {{ $t("Published matching, labeling and preprocessing definitions cannot be edited here.") }} </div>
  </section>
</template>
