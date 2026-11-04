<!-- Share search, status filters and row actions across list pages. -->
<script>
import { useWorkspace } from "../state/workspace.js";
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
      <span>{{ rows.length }} records</span>
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
      ><button
        class="secondary"
        @click="
          search = '';
          status = 'All statuses';
        "
      >
        Reset
      </button>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Identifier</th>
            <th>{{ page[0] === "A07" ? "Time / Actor" : "Name / Context" }}</th>
            <th>{{ page[0] === "A07" ? "Action" : "Version / Type" }}</th>
            <th>Status</th>
            <th>Details</th>
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
                  ? r.subjects + " subjects"
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
              <button
                v-else-if="page[0] === 'V01'"
                class="text-button"
                @click="go('/reviewer/tasks/' + r.id)"
              >
                Open →
              </button>
              <label v-else-if="page[0] === 'R04'" class="check"
                ><input
                  type="checkbox"
                  :disabled="r.status !== 'Eligible'"
                  :checked="selected.includes(r.id)"
                  @change="select(r.id)"
                />Select</label
              >
              <button
                v-else-if="page[0] === 'A03'"
                class="text-button"
                :disabled="r.status === 'Retired'"
                @click="release(r)"
              >
                {{
                  { Tested: "Approve", Approved: "Activate", Active: "Retire" }[
                    r.status
                  ] || "Retired"
                }}
              </button>
              <button
                v-else-if="page[0] === 'A06'"
                class="text-button"
                :disabled="!r.retryable || r.status === 'Queued'"
                @click="drawer = { kind: 'retry', item: r }"
              >
                {{ r.status === "Queued" ? "Queued" : "Retry" }}
              </button>
              <button
                v-else
                class="text-button"
                @click="
                  drawer = {
                    kind: page[0] === 'A01' ? 'user' : 'details',
                    item: r,
                  };
                  role = r.role || 'Researcher';
                "
              >
                View →
              </button>
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
    <div class="table-footer">
      <span>Showing {{ rows.length }} demo records</span
      ><span v-if="page[0] === 'R04'"
        >{{ selected.length }} selected · Eligibility checked</span
      ><span v-else
        >Page 1 <button class="page-number" disabled>1</button></span
      >
    </div>
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
