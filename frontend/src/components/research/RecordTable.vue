<!-- Share search, status filters and row actions across list pages. -->
<script>
import { useWorkspace } from "../../composables/useResearchWorkspace.js";
export default { setup: useWorkspace };
</script>

<template>
  <section class="panel">
    <div class="panel-title">
      <h2>
        {{
          {
            R01: "Authorized experiments",
            R04: "Eligible research subjects",
            R05: "Model registry",
            V01: "Assigned review tasks",
            A01: "Accounts",
            A03: "Release registry",
            A06: "Failed processing tasks",
            A07: "Audit events",
          }[page[0]]
        }}
      </h2>
      <span>{{ filtered.length }} records</span>
    </div>
    <div v-if="page[0] === 'R04'" class="tabs">
      <button
        v-for="t in ['Reviewed Profiles', 'Authorized Candidate Cases']"
        :class="{ active: source === t }"
        @click="source = t"
      >
        {{ t }}
      </button>
    </div>
    <div class="toolbar">
      <input
        v-model="search"
        placeholder="Search by name or identifier…"
        aria-label="Search records"
      /><select
        v-if="page[0] !== 'A07'"
        v-model="status"
        aria-label="Filter by status"
      >
        <option>All statuses</option>
        <option v-for="s in statuses">{{ s }}</option></select
      ><select v-if="page[0] === 'R01'" v-model="job" aria-label="Filter by job"><option>All jobs</option><option v-for="value in jobs" :key="value">{{ value }}</option></select><select v-model="sort" aria-label="Sort records"><option value="latest">Newest first</option><option value="oldest">Oldest first</option></select><button class="secondary" @click="reset">Reset</button>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Identifier</th>
            <th>{{ page[0] === "A07" ? "Time / Actor" : "Name / Context" }}</th>
            <th>{{ page[0] === "A07" ? "Action" : "Version / Type" }}</th>
            <th>Status</th>
            <th>Details / Frozen Configuration</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td class="identifier">{{ r.id }}</td>
            <td>
              <strong>{{ r.name }}</strong
              ><small>{{ r.date || r.actor || r.source }}</small>
            </td>
            <td>
              {{
                r.version ||
                r.type ||
                r.mode ||
                r.role ||
                r.action ||
                r.code ||
                r.schema
              }}
            </td>
            <td>
              <span class="badge" :class="badge(r.status)">{{ r.status }}</span>
            </td>
            <td>
              {{
                page[0] === "R01"
                  ? r.subjects + " subjects · " + r.job + " · " + r.weight + " · " + r.protocol + " · " + r.neutral + " · " + r.biased
                  : page[0] === "A06"
                    ? "Cleanup: " + r.cleanup
                    : page[0] === "A07"
                      ? r.resource
                      : r.protocol || r.skills || "Assigned access"
              }}
            </td>
            <td>
              <button
                v-if="page[0] === 'R01'"
                class="text-button"
                @click="go('/research/experiments/' + r.id)"
              >
                View →
              </button>
              <template v-else-if="page[0] === 'R04'"><label class="check"><input type="checkbox" :checked="selected.includes(r.id)" @change="select(r.id)" />Select</label><button class="text-button" @click="view(r)">View Details</button></template>
              <button v-else class="text-button" @click="view(r)">View Details</button>
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="6" class="empty">
              No matching records. Change your search or filters.
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="table-footer"><span>Showing {{ rows.length }} of {{ filtered.length }} demo records</span><label>Rows per page<select v-model.number="pageSize"><option :value="5">5</option><option :value="10">10</option><option :value="20">20</option></select></label><button class="secondary" :disabled="currentPage === 1" @click="currentPage--">Previous</button><span>Page {{ currentPage }}</span><button class="secondary" :disabled="currentPage * pageSize >= filtered.length" @click="currentPage++">Next</button></div>
    <div v-if="page[0] === 'R04'" class="toolbar"><label>Target draft experiment<select v-model="targetExperiment"><option value="">Select draft experiment</option><option v-for="row in drafts" :key="row.id" :value="row.id">{{ row.name }}</option></select></label><button class="primary" :disabled="!selected.length || !targetExperiment" @click="addSubjects">Add Selected Subjects to Experiment</button></div>
    <div class="panel-body callout" v-if="page[0] === 'R05'">
      32 → 16 → 8 → 1 network. Separate trained parameters. This registry is
      read-only.
    </div>
    <div class="panel-body callout" v-if="page[0] === 'A07'">
      Production audit queries use server-side filters. These demo records
      contain metadata only.
    </div>
  </section>
</template>
